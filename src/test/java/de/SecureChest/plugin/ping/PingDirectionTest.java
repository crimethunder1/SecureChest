package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ping.PingDirection.HeightHint;
import de.SecureChest.plugin.ping.PingDirection.Sector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PingDirection: Richtung relativ zur Blickrichtung, Hoehe und Abstand")
class PingDirectionTest {

    private static final double TOLERANCE = 1.0e-9;

    private static final double[] YAWS = {
            -720.0, -540.0, -360.0, -270.0, -180.0, -135.0, -90.0, -45.0, -12.5,
            0.0, 33.0, 90.0, 179.9, 180.0, 270.0, 360.0, 450.0, 720.0};

    private static double deltaXFor(double yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees)) * 10.0;
    }

    private static double deltaZFor(double yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees)) * 10.0;
    }

    private static Sector sectorForTargetAt(double playerYaw, double relativeDegrees) {
        double targetYaw = playerYaw + relativeDegrees;
        return PingDirection.sectorFor(
                PingDirection.relativeAngleDegrees(playerYaw, deltaXFor(targetYaw), deltaZFor(targetYaw)));
    }

    static Stream<Arguments> everyYawWithEveryRelativeDirection() {
        List<Arguments> cases = new ArrayList<>();
        Object[][] relativeDirections = {
                {0.0, Sector.AHEAD}, {40.0, Sector.AHEAD_RIGHT}, {90.0, Sector.RIGHT},
                {135.0, Sector.BEHIND_RIGHT}, {180.0, Sector.BEHIND}, {-135.0, Sector.BEHIND_LEFT},
                {-90.0, Sector.LEFT}, {-40.0, Sector.AHEAD_LEFT}};
        for (double yaw : YAWS) {
            for (Object[] relativeDirection : relativeDirections) {
                cases.add(Arguments.of(yaw, relativeDirection[0], relativeDirection[1]));
            }
        }
        return cases.stream();
    }

    @Test
    @DisplayName("Blick nach Sueden (Yaw 0): Ziel im Sueden geradeaus, im Westen rechts, im Osten links, im Norden hinten")
    void yawZeroCardinalTargets() {
        assertEquals(0.0, PingDirection.relativeAngleDegrees(0.0, 0.0, 10.0), TOLERANCE);
        assertEquals(90.0, PingDirection.relativeAngleDegrees(0.0, -10.0, 0.0), TOLERANCE);
        assertEquals(-90.0, PingDirection.relativeAngleDegrees(0.0, 10.0, 0.0), TOLERANCE);
        assertEquals(180.0, Math.abs(PingDirection.relativeAngleDegrees(0.0, 0.0, -10.0)), TOLERANCE);
    }

    @Test
    @DisplayName("Blick nach Westen (Yaw 90): Norden liegt rechts, Sueden links")
    void yawNinetyFacingWest() {
        assertEquals(0.0, PingDirection.relativeAngleDegrees(90.0, -10.0, 0.0), TOLERANCE);
        assertEquals(Sector.RIGHT, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(90.0, 0.0, -10.0)));
        assertEquals(Sector.LEFT, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(90.0, 0.0, 10.0)));
    }

    @Test
    @DisplayName("Negativer Yaw -90 (Blick nach Osten): Osten geradeaus, Sueden rechts, Norden links")
    void negativeYawFacingEast() {
        assertEquals(0.0, PingDirection.relativeAngleDegrees(-90.0, 10.0, 0.0), TOLERANCE);
        assertEquals(Sector.RIGHT, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(-90.0, 0.0, 10.0)));
        assertEquals(Sector.LEFT, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(-90.0, 0.0, -10.0)));
    }

    @Test
    @DisplayName("Blick nach Norden mit Yaw 180 und -180: Norden geradeaus")
    void yawPlusMinusOneEightyFacingNorth() {
        assertEquals(Sector.AHEAD, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(180.0, 0.0, -10.0)));
        assertEquals(Sector.AHEAD, PingDirection.sectorFor(PingDirection.relativeAngleDegrees(-180.0, 0.0, -10.0)));
    }

    @ParameterizedTest(name = "Yaw {0}, Ziel {1} Grad relativ: {2}")
    @MethodSource("everyYawWithEveryRelativeDirection")
    @DisplayName("Jede Blickrichtung, auch negativ und ueber 360 Grad, ergibt fuer jede relative Lage denselben Sektor")
    void everyYawGivesSameRelativeSector(double yaw, double relativeDegrees, Sector expected) {
        assertEquals(expected, sectorForTargetAt(yaw, relativeDegrees));
    }

    @ParameterizedTest(name = "{0} Grad: {1}")
    @CsvSource({
            "0.0, AHEAD",
            "12.0, AHEAD",
            "-12.0, AHEAD",
            "12.001, AHEAD_RIGHT",
            "-12.001, AHEAD_LEFT",
            "67.5, AHEAD_RIGHT",
            "-67.5, AHEAD_LEFT",
            "67.501, RIGHT",
            "-67.501, LEFT",
            "112.5, RIGHT",
            "-112.5, LEFT",
            "112.501, BEHIND_RIGHT",
            "-112.501, BEHIND_LEFT",
            "157.5, BEHIND_RIGHT",
            "-157.5, BEHIND_LEFT",
            "157.501, BEHIND",
            "-157.501, BEHIND",
            "180.0, BEHIND",
            "-180.0, BEHIND"
    })
    @DisplayName("Sektorgrenzen: Grenzwert gehoert zum inneren Sektor, knapp darueber zum naechsten")
    void sectorBoundaries(double relativeDegrees, Sector expected) {
        assertEquals(expected, PingDirection.sectorFor(relativeDegrees));
    }

    @ParameterizedTest(name = "{0} Grad: {1}")
    @CsvSource({
            "372.0, AHEAD",
            "-348.0, AHEAD",
            "350.0, AHEAD",
            "-350.0, AHEAD",
            "181.0, BEHIND",
            "-181.0, BEHIND",
            "540.0, BEHIND",
            "450.0, RIGHT",
            "-450.0, LEFT",
            "-270.0, RIGHT",
            "270.0, LEFT"
    })
    @DisplayName("Wraparound: Winkel ausserhalb von plus/minus 180 Grad landen im richtigen Sektor")
    void sectorWraparound(double relativeDegrees, Sector expected) {
        assertEquals(expected, PingDirection.sectorFor(relativeDegrees));
    }

    @Test
    @DisplayName("Wraparound bei plus/minus 180 Grad: Yaw 179 und Ziel bei -179 liegt 2 Grad rechts")
    void yawWraparoundAcrossOneEighty() {
        assertEquals(2.0, PingDirection.relativeAngleDegrees(179.0, deltaXFor(-179.0), deltaZFor(-179.0)), 1.0e-6);
        assertEquals(-2.0, PingDirection.relativeAngleDegrees(-179.0, deltaXFor(179.0), deltaZFor(179.0)), 1.0e-6);
        assertEquals(Sector.AHEAD, sectorForTargetAt(179.0, 2.0));
        assertEquals(Sector.AHEAD, sectorForTargetAt(-179.0, -2.0));
    }

    @ParameterizedTest(name = "{0} wird zu {1}")
    @CsvSource({
            "0.0, 0.0",
            "180.0, 180.0",
            "-180.0, 180.0",
            "190.0, -170.0",
            "-190.0, 170.0",
            "360.0, 0.0",
            "540.0, 180.0",
            "-540.0, 180.0",
            "725.0, 5.0",
            "-725.0, -5.0"
    })
    @DisplayName("normalizeDegrees liefert Werte von ueber -180 bis einschliesslich 180")
    void normalizeDegrees(double input, double expected) {
        assertEquals(expected, PingDirection.normalizeDegrees(input), TOLERANCE);
    }

    @ParameterizedTest(name = "Ziel {0} Grad relativ")
    @ValueSource(doubles = {-170.0, -100.0, -30.0, 0.0, 5.0, 60.0, 120.0, 175.0})
    @DisplayName("Relativer Winkel kommt bei jeder Blickrichtung unveraendert heraus")
    void relativeAngleRoundTrip(double relativeDegrees) {
        for (double yaw : YAWS) {
            double targetYaw = yaw + relativeDegrees;
            assertEquals(relativeDegrees,
                    PingDirection.relativeAngleDegrees(yaw, deltaXFor(targetYaw), deltaZFor(targetYaw)), 1.0e-6,
                    "Yaw " + yaw);
        }
    }

    @Test
    @DisplayName("Ziel 1 Block hoeher: kein Hoehenhinweis")
    void heightOneBlockAboveNoHint() {
        PingDirection direction = PingDirection.calculate(0.5, 64.0, 0.5, 0.0, 0.5, 65.5, 20.5);

        assertEquals(1, direction.heightDifferenceBlocks());
        assertEquals(HeightHint.NONE, direction.heightHint());
    }

    @Test
    @DisplayName("Ziel genau 2 Bloecke hoeher: Hinweis nach oben mit 2")
    void heightTwoBlocksAboveHint() {
        PingDirection direction = PingDirection.calculate(0.5, 64.0, 0.5, 0.0, 0.5, 66.5, 20.5);

        assertEquals(2, direction.heightDifferenceBlocks());
        assertEquals(HeightHint.ABOVE, direction.heightHint());
    }

    @Test
    @DisplayName("Ziel 1 Block tiefer: kein Hinweis, 2 Bloecke tiefer: Hinweis nach unten")
    void heightBelowThreshold() {
        PingDirection oneBelow = PingDirection.calculate(0.5, 64.0, 0.5, 0.0, 0.5, 63.5, 20.5);
        PingDirection twoBelow = PingDirection.calculate(0.5, 64.0, 0.5, 0.0, 0.5, 62.5, 20.5);

        assertEquals(-1, oneBelow.heightDifferenceBlocks());
        assertEquals(HeightHint.NONE, oneBelow.heightHint());
        assertEquals(-2, twoBelow.heightDifferenceBlocks());
        assertEquals(HeightHint.BELOW, twoBelow.heightHint());
    }

    @Test
    @DisplayName("Hoehe in Blockebenen: Fussposition 64.9 und Ziel auf Ebene 66 ergibt 2, 63.99 und Ebene 64 ergibt 1")
    void heightUsesBlockLevels() {
        assertEquals(2, PingDirection.calculate(0.5, 64.9, 0.5, 0.0, 0.5, 66.5, 20.5).heightDifferenceBlocks());
        assertEquals(1, PingDirection.calculate(0.5, 63.99, 0.5, 0.0, 0.5, 64.5, 20.5).heightDifferenceBlocks());
    }

    @Test
    @DisplayName("Negative Hoehen: Spieler auf -11, Ziel auf -13 ergibt Hinweis nach unten mit 2")
    void heightWithNegativeCoordinates() {
        PingDirection direction = PingDirection.calculate(0.5, -10.5, 0.5, 0.0, 0.5, -12.5, 20.5);

        assertEquals(-2, direction.heightDifferenceBlocks());
        assertEquals(HeightHint.BELOW, direction.heightHint());
    }

    @Test
    @DisplayName("Abstand ist 3D und wird als ganze Zahl abgeschnitten")
    void distanceIsThreeDimensional() {
        PingDirection direction = PingDirection.calculate(0.0, 64.0, 0.0, 0.0, 3.0, 68.0, 12.0);
        PingDirection almostFourteen = PingDirection.calculate(0.0, 64.0, 0.0, 0.0, 0.0, 64.0, 13.9);
        PingDirection straightUp = PingDirection.calculate(0.0, 64.0, 0.0, 0.0, 0.0, 74.0, 0.0);

        assertEquals(13.0, direction.distance(), TOLERANCE);
        assertEquals(13, direction.distanceBlocks());
        assertEquals(13, almostFourteen.distanceBlocks());
        assertEquals(10, straightUp.distanceBlocks());
    }

    @Test
    @DisplayName("calculate verbindet Sektor, Hoehe und Abstand")
    void calculateCombinesEverything() {
        PingDirection direction = PingDirection.calculate(0.5, 60.0, 0.5, 90.0, -19.5, 64.5, 0.5);

        assertEquals(Sector.AHEAD, direction.sector());
        assertEquals(HeightHint.ABOVE, direction.heightHint());
        assertEquals(4, direction.heightDifferenceBlocks());
        assertEquals(20, direction.distanceBlocks());
    }
}
