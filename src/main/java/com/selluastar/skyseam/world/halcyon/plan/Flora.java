package com.selluastar.skyseam.world.halcyon.plan;

/**
 * The giant flora the plan places (docs/DECISIONS.md K85): trees and mushrooms far bigger than a decoration can be,
 * each grown from its own seed and written chunk by chunk ({@code decor.GiantTrees}, {@code decor.GiantMushrooms}), so
 * their size is not limited by vanilla's 3-by-3-chunk decoration window.
 */
public final class Flora {
    private Flora() {}

    public enum Kind {
        /** The Elderwood's giant (image 8): a flared buttress trunk with glowing veins and a vast layered canopy. */
        ELDER_LUMEN(20),
        /** Petalwash's great tree: a multi-domed violet crown hung with wisteria. */
        GREAT_VIOLET(15),
        /** The Cirrus Reefs' huge layered white umbrellas (image 6's trees, grown giant). */
        GRAND_BLOSSOM(15),
        /** The Hush's drooping wisteria giant, lit by lantern vines. */
        WEEPING_DUSK(14),
        /** The Sunreach Cliffs' tall dark conifers (image 11). */
        SPIREPINE(7),
        /** The Capwood's mushrooms (image 10): the orange-red Ember Cap with white spots. */
        EMBER_CAP(10),
        /** A tall, wide, flat cream Parasol with brown scales. */
        PARASOL(12),
        /** A glowing violet Glowcap, in the Hush and the Capwood. */
        GLOWCAP(8),
        /** A conical lilac Bellcap. */
        BELLCAP(8),
        /** Three to six mushrooms of mixed kinds, grown from one spot. */
        MUSHROOM_CLUSTER(16);

        /** How far the flora reaches sideways from its base. */
        public final int reach;

        Kind(int reach) {
            this.reach = reach;
        }

        public boolean isMushroom() {
            return ordinal() >= EMBER_CAP.ordinal();
        }
    }

    /** One giant: its kind, its base (the ground block it stands on), and its seed. */
    public record Big(Kind kind, int x, int groundY, int z, long seed) {
        public boolean touches(int x0, int z0, int x1, int z1) {
            return x + kind.reach >= x0 && x - kind.reach <= x1 && z + kind.reach >= z0 && z - kind.reach <= z1;
        }
    }

    /** The widest any giant reaches, for finding the ones near a chunk. */
    public static final int MAX_REACH = 20;
}
