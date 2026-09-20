package com.goshan.playerinvasion.entity.ai;

import com.goshan.playerinvasion.PIConfig;
import com.goshan.playerinvasion.PlayerInvasion;
import com.goshan.playerinvasion.entity.InvaderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a player does when walking does not get them to the target: tower up
 * (target above), bridge (gap), or mine through (wall, ceiling, floor - a
 * staircase down when the target is underground). Shared by the chase and the
 * hunt goals. Once a method is chosen it is kept until it is exhausted, so a
 * jump or a one-block fall does not reset the "stuck" verdict.
 */
public final class ObstacleSolver {

    private static final int STUCK_TICKS = 15;
    private static final int PILLAR_TIMEOUT = 400;

    private final InvaderEntity bot;
    private final BlockBreaker breaker;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private boolean digging;
    private boolean pillaring;
    private int pillarTicks;
    private int pillarCooldown;
    private int digGrace;
    private int lastTick = -100;

    public ObstacleSolver(InvaderEntity bot) {
        this.bot = bot;
        this.breaker = new BlockBreaker(bot);
    }

    public boolean isBusy() {
        return digging || pillaring;
    }

    public void reset() {
        stuckTicks = 0;
        lastPos = bot.position();
        digging = false;
        pillaring = false;
        pillarTicks = 0;
        digGrace = 0;
        breaker.reset();
    }

    private static boolean placingAllowed() {
        return PIConfig.loaded() && PIConfig.PILLAR_UP.get();
    }

    /**
     * Call every tick while chasing {@code goal}. Returns true when the solver took
     * over movement this tick (the caller must not path or strafe).
     *
     * @param closeEnough the caller already reaches the target - nothing to solve
     */
    public boolean tick(Vec3 goal, boolean closeEnough) {
        // shared between goals: a pause of a few ticks (goal switch) keeps the state, a longer one starts over
        if (bot.tickCount - lastTick > 5) {
            reset();
        }
        lastTick = bot.tickCount;
        if (closeEnough) {
            stuckTicks = 0;
            lastPos = bot.position();
            if (isBusy()) {
                pillaring = false;
                digging = false;
                breaker.reset();
            }
            return false;
        }

        if (pillaring) {
            if (pillarTick(goal)) {
                return true;
            }
            pillaring = false;
            stuckTicks = 0;
        }

        if (digging) {
            if (breaker.tickTowards(goal)) {
                digGrace = 0;
                return true;
            }
            // nothing left to break in this direction: let the navigation try, but
            // come back quickly if it is still hopeless
            breaker.reset();
            digging = false;
            digGrace = 10;
            stuckTicks = STUCK_TICKS - 5;
            return false;
        }

        boolean moved = bot.position().distanceToSqr(lastPos) > 0.0025D;
        lastPos = bot.position();
        if (moved && digGrace <= 0) {
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        if (digGrace > 0) {
            digGrace--;
        }
        if (stuckTicks < STUCK_TICKS) {
            return false;
        }

        Path path = bot.getNavigation().getPath();
        boolean pathFails = path == null || !path.canReach() || bot.getNavigation().isDone();
        if (!pathFails) {
            return false;
        }

        double dy = goal.y - bot.getY();
        double flat = Math.sqrt(Math.pow(goal.x - bot.getX(), 2) + Math.pow(goal.z - bot.getZ(), 2));

        if (placingAllowed() && dy > 2.0D && flat < 6.0D && canPillarHere()) {
            pillaring = true;
            pillarTicks = 0;
            pillarCooldown = 0;
            PlayerInvasion.LOGGER.debug("{} towers up ({} blocks to climb)", bot.getBotName(), Math.round(dy));
            return pillarTick(goal);
        }
        if (placingAllowed() && Placing.bridgeTowards(bot, goal)) {
            PlayerInvasion.LOGGER.debug("{} bridges a gap", bot.getBotName());
            stuckTicks = 0;
            return true;
        }
        if (BlockBreaker.enabled() && flat < 32.0D) {
            digging = true;
            if (breaker.tickTowards(goal)) {
                PlayerInvasion.LOGGER.debug("{} starts digging towards the target (dy {})", bot.getBotName(), Math.round(dy));
                return true;
            }
            breaker.reset();
            digging = false;
        }
        stuckTicks = 0;
        return false;
    }

    private boolean canPillarHere() {
        if (Placing.buildingBlock(bot).isEmpty()) {
            return false;
        }
        Level level = bot.level();
        BlockPos feet = bot.blockPosition();
        // need room to jump: the two blocks over the head must be free
        return Placing.isFree(level, feet.above(2)) && Placing.isFree(level, feet.above(3));
    }

    /** Jump, put a block underneath, repeat - the classic tower. Returns false when done or impossible. */
    private boolean pillarTick(Vec3 goal) {
        double dy = goal.y - bot.getY();
        double flat = Math.sqrt(Math.pow(goal.x - bot.getX(), 2) + Math.pow(goal.z - bot.getZ(), 2));
        if (dy < 1.0D || flat > 8.0D || ++pillarTicks > PILLAR_TIMEOUT || bot.isInWater()) {
            return false;
        }
        ItemStack blocks = Placing.buildingBlock(bot);
        if (blocks.isEmpty()) {
            return false;
        }
        Level level = bot.level();
        BlockPos feet = bot.blockPosition();
        if (bot.onGround() && !Placing.isFree(level, feet.above(2))) {
            return false; // ceiling: let the breaker open it
        }

        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0.0F, 0.0F);
        bot.getLookControl().setLookAt(goal.x, goal.y, goal.z, 30.0F, 30.0F);

        if (pillarCooldown > 0) {
            pillarCooldown--;
        }
        if (bot.onGround()) {
            if (pillarCooldown == 0) {
                bot.getJumpControl().jump();
                pillarCooldown = 2;
            }
            return true;
        }
        BlockPos below = feet.below();
        if (bot.getY() - below.getY() < 1.0D || bot.getDeltaMovement().y > 0.12D) {
            return true; // still rising
        }
        if (!Placing.isFree(level, below)) {
            return true;
        }
        if (!level.getEntitiesOfClass(Entity.class, new AABB(below), e -> e != bot).isEmpty()) {
            return true;
        }
        BlockItem item = (BlockItem) blocks.getItem();
        if (Placing.place(bot, below, item.getBlock().defaultBlockState(), item, null)) {
            pillarCooldown = 3;
        }
        return true;
    }
}
