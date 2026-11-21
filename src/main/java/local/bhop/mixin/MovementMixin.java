package local.bhop.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
abstract class MovementMixin {
    @Shadow private int noJumpDelay;
    @Shadow protected boolean jumping;

    @Unique
    private boolean bhop$active(LivingEntity entity) {
        return (Object) this instanceof LocalPlayer player
            && jumping && !player.getAbilities().flying && !player.isSpectator()
            && !player.isShiftKeyDown() && !player.isPassenger()
            && !entity.isInWater() && !entity.isInLava()
            && !entity.onClimbable() && !entity.isInPowderSnow;
    }

    @Inject(method = "aiStep()V", at = @At("HEAD"))
    private void bhop$allowLandingJump(CallbackInfo callback) {
        if (bhop$active((LivingEntity) (Object) this)) {
            noJumpDelay = 0;
        }
    }

    @ModifyVariable(method = "travelInAir", at = @At("STORE"), ordinal = 0)
    private float bhop$keepTakeoffMomentum(float friction, Vec3 input) {
        LivingEntity entity = (LivingEntity) (Object) this;
        return bhop$active(entity) && entity.getDeltaMovement().y > 0
            && input.horizontalDistanceSqr() > 0 ? 1.0f : friction;
    }

    @ModifyArg(method = "travelInAir", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;computeModifiedFriction(FF)F",
        ordinal = 1), index = 0)
    private float bhop$horizontalAirDrag(float drag) {
        return bhop$active((LivingEntity) (Object) this) ? 0.985f : drag;
    }

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void bhop$accelerate(LivingEntity entity, float speed, Vec3 input) {
        if (!bhop$active(entity) || (entity.onGround() && entity.getDeltaMovement().y <= 0)) {
            entity.moveRelative(speed, input);
            return;
        }

        double magnitude = input.horizontalDistance();
        if (magnitude < 1.0e-4) {
            return;
        }

        Vec3 direction = new Vec3(input.x / magnitude, 0, input.z / magnitude)
            .yRot((float) Math.toRadians(-entity.getYRot()));
        Vec3 velocity = entity.getDeltaMovement();
        double currentSpeed = velocity.horizontalDistance();
        double desiredAlongInput = 0.36 - velocity.dot(direction);
        double acceleration = Math.max(0, Math.min(desiredAlongInput, 0.032 * Math.min(magnitude, 1)));
        Vec3 next = velocity.add(direction.scale(acceleration));

        // Preserve external impulses while capping speed gained from input.
        double limit = Math.max(0.48, currentSpeed);
        double nextSpeed = next.horizontalDistance();
        if (nextSpeed > limit) {
            double scale = limit / nextSpeed;
            next = new Vec3(next.x * scale, next.y, next.z * scale);
        }
        entity.setDeltaMovement(next);
    }
}
