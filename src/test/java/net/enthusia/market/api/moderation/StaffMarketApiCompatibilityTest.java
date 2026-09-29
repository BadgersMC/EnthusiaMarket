package net.enthusia.market.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the bean-style value shapes consumed by EnthusiaStaff reflection. */
class StaffMarketApiCompatibilityTest {

    StaffMarketApiCompatibilityTest() {
    }

    @Test
    void stallIdBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        assertEquals("stall-1", invoke(ownedStall(), "getId"));
    }

    @Test
    void stallWorldBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        assertEquals("world", invoke(ownedStall(), "getWorld"));
    }

    @Test
    void stallStateBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        assertEquals("OWNED", invoke(ownedStall(), "getState"));
    }

    @Test
    void ownershipTypeBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        final Object owner = invoke(ownedStall(), "getOwnership");
        assertEquals(MarketOwnership.Type.SOLO, invoke(owner, "getType"));
    }

    @Test
    void ownershipIdBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        final Object owner = invoke(ownedStall(), "getOwnership");
        assertEquals("owner-1", invoke(owner, "getId"));
    }

    @Test
    void blacklistStatusBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        assertEquals(StallBlacklistState.Status.ACTIVE, invoke(activeBlacklist(), "getStatus"));
    }

    @Test
    void blacklistExpirationBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        final Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
        assertEquals(expiry, invoke(activeBlacklist(), "getExpiresAt"));
    }

    @Test
    void blacklistCaseBeanAliasMatchesStaffShape() throws ReflectiveOperationException {
        assertEquals("ES-CASE-1", invoke(activeBlacklist(), "getCaseId"));
    }

    @Test
    void recordStyleOwnershipAccessorRemainsOptional() {
        final MarketOwnership ownership = ownedStall().ownership();
        assertEquals(Optional.of("owner-1"), ownership.id());
    }

    @Test
    void recordStyleBlacklistExpirationRemainsOptional() {
        final Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
        assertEquals(Optional.of(expiry), activeBlacklist().expiresAt());
    }

    @Test
    void unownedBeanAliasReturnsNullIdentity() {
        final MarketOwnership ownership = new MarketOwnership(MarketOwnership.Type.NONE, Optional.empty());
        assertNull(ownership.getId());
    }

    @Test
    void removedBlacklistBeanAliasReturnsNullExpiration() {
        assertNull(removedBlacklist().getExpiresAt());
    }

    private static MarketStallRecord ownedStall() {
        final MarketOwnership ownership = new MarketOwnership(
                MarketOwnership.Type.SOLO,
                Optional.of("owner-1")
        );
        return new MarketStallRecord(
                "stall-1",
                "world",
                "OWNED",
                ownership,
                3L,
                false,
                Optional.empty()
        );
    }

    private static StallBlacklistState activeBlacklist() {
        final Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
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

    private static StallBlacklistState removedBlacklist() {
        final Instant updated = Instant.parse("2026-10-01T00:00:00Z");
        return new StallBlacklistState(
                UUID.randomUUID(),
                StallBlacklistState.Status.REMOVED,
                Optional.empty(),
                "ES-CASE-2",
                UUID.randomUUID(),
                1L,
                updated
        );
    }

    private static Object invoke(final Object target, final String methodName)
            throws ReflectiveOperationException {
        final Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
