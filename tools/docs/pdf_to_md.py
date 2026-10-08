"""Convert the Skyseam build spec PDF (a Chromium/Skia print) into plain Markdown.

Standard library only. It reads each page's content stream, maps glyphs back to text through the
fonts' ToUnicode tables, groups glyph runs into lines by their baseline, joins table cells on one
baseline with " | ", and turns lines in a larger font into headings.

The output is a machine conversion for searching and quoting. The PDF stays the source of truth.

Usage: python tools/docs/pdf_to_md.py docs/Skyseam-Build-Spec.pdf docs/Skyseam-Build-Spec.md
"""
import re
import sys
import zlib

TOKEN = re.compile(
    rb"<<|>>|<[0-9A-Fa-f\s]*>|\((?:\\.|[^\\)])*\)|\[|\]|/[^\s/\[\]()<>]+|[-+]?\d*\.?\d+|[A-Za-z'\"*]+"
)
NUMBER = re.compile(rb"[-+]?\d*\.?\d+")
FOOTER = re.compile(r"\s*(Skyseam Build Spec|Page \d+ \|?\s*of \d+)\s*")


class Pdf:
    def __init__(self, data):
        self.objs = {
            int(m.group(1)): m.group(2)
            for m in re.finditer(rb"(\d+) 0 obj\s*(.*?)endobj", data, re.S)
        }
        self._cmaps = {}

    def stream(self, n):
        o = self.objs[n]
        i = o.find(b"stream")
        head, body = o[:i], o[i + 6:]
        if body.startswith(b"\r\n"):
            body = body[2:]
        elif body.startswith(b"\n"):
            body = body[1:]
        length = re.search(rb"/Length\s+(\d+)(\s+0\s+R)?", head)
        if length and not length.group(2):
            body = body[: int(length.group(1))]
        else:
            body = body[: body.rfind(b"endstream")]
        if b"FlateDecode" in head:
            body = zlib.decompressobj().decompress(body)
        return body

    def pages(self):
        root = next(
            n for n, o in self.objs.items()
            if re.search(rb"/Type\s*/Pages\b", o) and b"/Parent" not in o
        )

        def walk(n):
            o = self.objs[n]
            if re.search(rb"/Type\s*/Pages\b", o):
                kids = re.search(rb"/Kids\s*\[(.*?)\]", o, re.S).group(1)
                out = []
                for ref in re.findall(rb"(\d+)\s+0\s+R", kids):
                    out += walk(int(ref))
                return out
            return [n]

        return walk(root)

    def cmap(self, font_obj):
        if font_obj in self._cmaps:
            return self._cmaps[font_obj]
        mapping = {}
        ref = re.search(rb"/ToUnicode\s+(\d+)\s+0\s+R", self.objs[font_obj])
        if ref:
            s = self.stream(int(ref.group(1))).decode("latin1")
            for block in re.findall(r"beginbfchar(.*?)endbfchar", s, re.S):
                for a, b in re.findall(r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", block):
                    mapping[int(a, 16)] = bytes.fromhex(b).decode("utf-16-be", "replace")
            for block in re.findall(r"beginbfrange(.*?)endbfrange", s, re.S):
                for a, b, c in re.findall(
                    r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*(<[0-9A-Fa-f]+>|\[[^\]]*\])", block
                ):
                    lo, hi = int(a, 16), int(b, 16)
                    if c.startswith("<"):
                        base = bytes.fromhex(c[1:-1]).decode("utf-16-be", "replace")
                        for k in range(hi - lo + 1):
                            mapping[lo + k] = base[:-1] + chr(ord(base[-1]) + k)
                    else:
                        for k, h in enumerate(re.findall(r"<([0-9A-Fa-f]+)>", c)):
                            mapping[lo + k] = bytes.fromhex(h).decode("utf-16-be", "replace")
        self._cmaps[font_obj] = mapping
        return mapping


def mul(m, n):
    a, b, c, d, e, f = m
    A, B, C, D, E, F = n
    return (a * A + b * C, a * B + b * D, c * A + d * C, c * B + d * D, e * A + f * C + E, e * B + f * D + F)


def decode_hex(token, mapping):
    h = re.sub(rb"\s", b"", token[1:-1]).decode()
    return "".join(mapping.get(int(h[i:i + 4], 16), "�") for i in range(0, len(h), 4))


def page_lines(pdf, page_obj):
    po = pdf.objs[page_obj]
    font_dict = re.search(rb"/Font\s*<<(.*?)>>", po, re.S)
    fonts = {}
    if font_dict:
        fonts = {k.decode(): int(v) for k, v in re.findall(rb"/(\w+)\s+(\d+)\s+0\s+R", font_dict.group(1))}
    content = pdf.stream(int(re.search(rb"/Contents\s+(\d+)\s+0\s+R", po).group(1)))

    ctm = (1, 0, 0, 1, 0, 0)
    saved = []
    tm = tlm = ctm
    mapping, font_size, mono = {}, 0.0, False
    stack, runs, new_block = [], [], False
    for t in TOKEN.findall(content):
        if t == b"q":
            saved.append(ctm); stack = []
        elif t == b"Q":
            ctm = saved.pop() if saved else ctm; stack = []
        elif t == b"cm":
            ctm = mul(tuple(stack[-6:]), ctm); stack = []
        elif t == b"BT":
            tm = tlm = (1, 0, 0, 1, 0, 0); new_block = True; stack = []
        elif t == b"Tf":
            name = stack[-2].decode()[1:]
            mapping = pdf.cmap(fonts[name]) if name in fonts else {}
            mono = name in fonts and b"Mono" in pdf.objs[fonts[name]]
            font_size = stack[-1]
            stack = []
        elif t == b"Tm":
            tm = tlm = tuple(stack[-6:]); stack = []
        elif t in (b"Td", b"TD"):
            tlm = mul((1, 0, 0, 1, stack[-2], stack[-1]), tlm); tm = tlm; stack = []
        elif t in (b"Tj", b"TJ"):
            m = mul(tm, ctm)
            text = "".join(decode_hex(a, mapping) for a in stack if isinstance(a, bytes) and a.startswith(b"<"))
            size = abs(font_size * m[3])
            runs.append((round(m[5], 1), m[4], new_block, size, mono, text))
            new_block = False
            stack = []
        elif t == b"[":
            stack = []
        elif t == b"]":
            pass
        elif NUMBER.fullmatch(t):
            stack.append(float(t))
        elif (t.startswith(b"<") and t != b"<<") or t.startswith(b"/"):
            stack.append(t)
        else:
            stack = []

    # A new text block on the same baseline is either inline code (it starts right after the previous
    # glyph, in the monospace font) or a new table cell (it starts after a column gap).
    lines, text, size, all_mono, last_y, last_x, line_mono = [], "", 0.0, True, None, 0.0, False
    for y, x, block, run_size, run_mono, run_text in runs:
        if last_y is None or abs(y - last_y) > 1.5:
            if text.strip():
                lines.append((text.rstrip() + ("`" if line_mono and not all_mono else ""), size, all_mono))
            text, size, all_mono, line_mono = run_text, run_size, run_mono, run_mono
            if run_mono:
                text = "`" + text
        else:
            inline = block and (x - last_x) < 2.2 * max(size, run_size)
            if run_mono != line_mono and (inline or not block):
                text += "`"
            elif block and text and not inline:
                if line_mono:
                    text += "`"
                text += " | " if not text.endswith(" ") else "| "
                if run_mono:
                    text += "`"
            text += run_text
            size = max(size, run_size)
            all_mono = all_mono and run_mono
            line_mono = run_mono
        last_y, last_x = y, x
    if text.strip():
        lines.append((text.rstrip() + ("`" if line_mono and not all_mono else ""), size, all_mono))
    cleaned = []
    for t, s, m in lines:
        if m:
            t = t.strip("`")
        t = t.replace("` `", " ").replace("``", "")
        if not FOOTER.fullmatch(t.replace("`", "")):
            cleaned.append((t, s, m))
    return cleaned


def convert(pdf_path, md_path):
    pdf = Pdf(open(pdf_path, "rb").read())
    pages = pdf.pages()
    body_sizes = {}
    all_lines = []
    for number, page in enumerate(pages, 1):
        lines = page_lines(pdf, page)
        all_lines.append((number, lines))
        for _, s, _ in lines:
            body_sizes[round(s, 1)] = body_sizes.get(round(s, 1), 0) + 1
    body = max(body_sizes, key=body_sizes.get)

    out = [
        "<!-- Machine conversion of docs/Skyseam-Build-Spec.pdf by tools/docs/pdf_to_md.py. -->",
        "<!-- The PDF is the source of truth: if this file and the PDF disagree, the PDF wins. -->",
        "<!-- Tables are flattened: cells on one line are joined with ' | ', and wrapped cells continue on the next lines. -->",
        "",
    ]
    for number, lines in all_lines:
        out.append(f"<!-- page {number} of {len(pages)} -->")
        # Group consecutive lines set entirely in the monospace font. Long or multi-line groups are
        # real code blocks; short ones are inline code (ids, paths, tags) inside paragraphs or table cells.
        groups = []
        for line in lines:
            if groups and groups[-1][0] == line[2]:
                groups[-1][1].append(line)
            else:
                groups.append((line[2], [line]))
        for mono, group in groups:
            if mono and (len(group) >= 3 or any(len(t) >= 40 for t, _, _ in group)):
                out.append("```text")
                out += [t[:-1].rstrip() if t.endswith("|") else t for t, _, _ in group]
                out.append("```")
                continue
            for text, size, _ in group:
                if mono and "`" not in text:
                    out.append(f"`{text}`")
                elif size >= body * 1.9:
                    out += ["", f"# {text}", ""]
                elif size >= body * 1.35:
                    out += ["", f"## {text}", ""]
                elif size >= body * 1.12:
                    out += ["", f"### {text}", ""]
                else:
                    out.append("\\" + text if text.startswith("#") else text)
        out.append("")
    with open(md_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))
    return len(pages), body


if __name__ == "__main__":
    n, body = convert(sys.argv[1], sys.argv[2])
    print(f"converted {n} pages, body font size {body}")
