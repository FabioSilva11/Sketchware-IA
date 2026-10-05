package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Level-1 tests for the M3 RunBudget (Cookbook spending-controller port). */
public class RunBudgetTest {

    @Test
    public void reserveAndSettle_accountsActualUsage() {
        RunBudget budget = new RunBudget(10_000);
        RunBudget.Handle handle = budget.reserve(4_000);
        budget.settle(handle, 2_500, false); // provider-reported

        assertEquals(2_500, budget.spent());
        assertEquals(7_500, budget.remaining());
    }

    @Test
    public void reserveBlocksWhenWorstCaseExceedsRemaining() {
        RunBudget budget = new RunBudget(5_000);
        budget.reserve(3_000);
        try {
            budget.reserve(3_000); // 3000 pending + 3000 > 5000
            fail("expected BudgetExceededException");
        } catch (RunBudget.BudgetExceededException expected) {
            assertTrue(expected.getMessage().contains("budget exhausted"));
        }
    }

    @Test
    public void settleOverReservationBlocksRunPermanently() {
        RunBudget budget = new RunBudget(10_000);
        RunBudget.Handle handle = budget.reserve(1_000);
        try {
            budget.settle(handle, 2_000, false); // actual > reserved
            fail("expected UncertainChargeException");
        } catch (RunBudget.UncertainChargeException expected) {
            // ok
        }
        assertTrue(budget.isBlocked());
        try {
            budget.reserve(1);
            fail("blocked run must not accept reservations");
        } catch (RunBudget.BudgetExceededException expected) {
            // ok
        }
    }

    @Test
    public void settleTwiceIsRejectedAndBlocks() {
        RunBudget budget = new RunBudget(10_000);
        RunBudget.Handle handle = budget.reserve(1_000);
        budget.settle(handle, 900, false);
        try {
            budget.settle(handle, 900, false); // unknown handle
            fail("expected UncertainChargeException");
        } catch (RunBudget.UncertainChargeException expected) {
            // ok
        }
        assertTrue(budget.isBlocked());
    }

    @Test
    public void blockFailsClosedRegardlessOfRemaining() {
        RunBudget budget = new RunBudget(100_000);
        budget.block();
        try {
            budget.ensureActive(1);
            fail("expected BudgetExceededException");
        } catch (RunBudget.BudgetExceededException expected) {
            // ok
        }
    }

    @Test
    public void nullOrNegativeSettlementBlocks() {
        RunBudget budget = new RunBudget(10_000);
        RunBudget.Handle handle = budget.reserve(1_000);
        try {
            budget.settle(handle, -5, false);
            fail("expected UncertainChargeException");
        } catch (RunBudget.UncertainChargeException expected) {
            // ok
        }
        assertTrue(budget.isBlocked());
    }
}
