package net.enthusia.market.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies validation and time-bound behavior exposed by the moderation API records. */
class MarketModerationContractTest {
    /** Stable SHA-256-shaped fixture used by destructive operation records. */
    private static final String CHECKSUM = "a".repeat(64);

    MarketModerationContractTest() {
    }

    @Test
    void unownedOwnershipAcceptsNoIdentity() {
        assertTrue(new MarketOwnership(MarketOwnership.Type.NONE, Optional.empty()).id().isEmpty());
    }

    @Test
    void ownedOwnershipRequiresIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketOwnership(MarketOwnership.Type.SOLO, Optional.empty())
        );
    }

    @Test
    void unownedOwnershipRejectsIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketOwnership(MarketOwnership.Type.NONE, Optional.of("unexpected"))
        );
    }

    @Test
    void operationRequestBoundsRecoveryWindow() {
        final Instant review = Instant.parse("2026-08-20T00:00:00Z");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketOperationRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ES-CASE-1",
                        "stall-1",
                        review,
                        review.plusSeconds(32L * 86_400L),
                        Optional.empty()
                )
        );
    }

    @Test
    void identifiersRejectAsciiWhitespace() {
        final Instant review = Instant.parse("2026-08-20T00:00:00Z");
        assertInvalidIdentifier("CASE 1", review);
    }

    @Test
    void identifiersRejectUnicodeWhitespace() {
        final Instant review = Instant.parse("2026-08-20T00:00:00Z");
        assertInvalidIdentifier("CASE\u20071", review);
    }

    @Test
    void blacklistIsActiveBeforeExpiration() {
        final Instant expiry = Instant.parse("2026-08-20T00:00:00Z");
        final StallBlacklistState state = activeBlacklist(expiry);
        assertTrue(state.activeAt(expiry.minusNanos(1L)));
    }

    @Test
    void blacklistIsInactiveAtExpiration() {
        final Instant expiry = Instant.parse("2026-08-20T00:00:00Z");
        final StallBlacklistState state = activeBlacklist(expiry);
        assertFalse(state.activeAt(expiry));
    }

    @Test
    void confiscationRequiresFullChecksum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketConfiscationApproval(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "short",
                        Instant.now()
                )
        );
    }

    @Test
    void restoreRetainsExpectedChecksum() {
        final MarketRestoreRequest request = new MarketRestoreRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CHECKSUM
        );
        assertEquals(CHECKSUM, request.expectedCurrentChecksum());
    }

    private static StallBlacklistState activeBlacklist(final Instant expiry) {
        return new StallBlacklistState(
                UUID.randomUUID(),
                StallBlacklistState.Status.ACTIVE,
                Optional.of(expiry),
                "ES-CASE-1",
                UUID.randomUUID(),
                1L,
                expiry.minusSeconds(60L)
        );
    }

    private static void assertInvalidIdentifier(final String caseId, final Instant review) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketOperationRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        caseId,
                        "stall-1",
                        review,
                        review.plusSeconds(86_400L),
                        Optional.empty()
                )
        );
    }
}
