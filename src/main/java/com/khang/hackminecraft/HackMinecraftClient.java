package com.khang.hackminecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class HackMinecraftClient implements ClientModInitializer {
    private static final Map<String, KeyBinding> TOGGLES = new LinkedHashMap<>();
    private static final Set<String> ENABLED = new HashSet<>();
    private double savedGamma = 1.0;
    private int crystalCooldown;
    private int totemCooldown;
    private int triggerCooldown;
    private int anchorStage;
    private int anchorTimeout;
    private BlockPos anchorPosition;
    private int antiAfkCooldown;

    @Override
    public void onInitializeClient() {
        registerToggle("ESP", GLFW.GLFW_KEY_O);
        registerToggle("TRACERS", GLFW.GLFW_KEY_T);
        registerToggle("AUTO_TOTEM", GLFW.GLFW_KEY_P);
        registerToggle("AUTO_CRYSTAL", GLFW.GLFW_KEY_K);
        registerToggle("AUTO_ANCHOR", GLFW.GLFW_KEY_J);
        registerToggle("FULLBRIGHT", GLFW.GLFW_KEY_L);
        registerToggle("AUTO_SPRINT", GLFW.GLFW_KEY_G);
        registerToggle("AUTO_EAT", GLFW.GLFW_KEY_H);
        registerToggle("AUTO_TOOL", GLFW.GLFW_KEY_I);
        registerToggle("TRIGGERBOT", GLFW.GLFW_KEY_B);
        registerToggle("AUTO_RESPAWN", GLFW.GLFW_KEY_R);
        registerToggle("HUD", GLFW.GLFW_KEY_U);
        registerToggle("ANTI_AFK", GLFW.GLFW_KEY_Y);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(this::renderEntities);
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderHud(drawContext));
    }

    private void registerToggle(String feature, int keyCode) {
        KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hackminecraft." + feature.toLowerCase(),
                InputUtil.Type.KEYSYM,
                keyCode,
                "category.hackminecraft"
        ));
        TOGGLES.put(feature, binding);
    }

    private void onClientTick(MinecraftClient client) {
        for (Map.Entry<String, KeyBinding> entry : TOGGLES.entrySet()) {
            while (entry.getValue().wasPressed()) toggle(entry.getKey(), client);
        }
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        if (totemCooldown > 0) totemCooldown--;
        if (crystalCooldown > 0) crystalCooldown--;
        if (triggerCooldown > 0) triggerCooldown--;
        if (antiAfkCooldown > 0) antiAfkCooldown--;

        if (enabled("AUTO_TOTEM") && totemCooldown == 0) {
            equipTotem(client);
            totemCooldown = 8;
        }

        if (enabled("AUTO_SPRINT")) {
            client.player.setSprinting(client.options.forwardKey.isPressed()
                    && !client.options.sneakKey.isPressed() && !client.player.isUsingItem());
        }

        if (enabled("AUTO_EAT")) handleAutoEat(client);
        else client.options.useKey.setPressed(false);

        if (enabled("AUTO_TOOL")) selectBestTool(client);

        if (enabled("TRIGGERBOT") && triggerCooldown == 0
                && client.options.attackKey.isPressed()
                && client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() != client.player) {
            client.interactionManager.attackEntity(client.player, hit.getEntity());
            client.player.swingHand(Hand.MAIN_HAND);
            triggerCooldown = 4;
        }

        if (enabled("AUTO_RESPAWN") && client.player.isDead()) client.player.requestRespawn();

        if (enabled("ANTI_AFK") && antiAfkCooldown == 0) {
            if (client.player.isOnGround() && !client.player.isUsingItem()) client.player.jump();
            antiAfkCooldown = 1200;
        }

        if (enabled("AUTO_CRYSTAL") && crystalCooldown == 0) {
            handleAutoCrystal(client);
            crystalCooldown = 4;
        }

        if (enabled("AUTO_ANCHOR")) handleAutoAnchor(client);
        else {
            anchorStage = 0;
            anchorPosition = null;
            anchorTimeout = 0;
        }
    }

    private void toggle(String feature, MinecraftClient client) {
        if (!ENABLED.add(feature)) ENABLED.remove(feature);

        if (feature.equals("FULLBRIGHT") && client.options != null) {
            if (enabled(feature)) {
                savedGamma = client.options.getGamma().getValue();
                client.options.getGamma().setValue(1.0);
            } else {
                client.options.getGamma().setValue(savedGamma);
            }
        }
        if (feature.equals("AUTO_EAT") && !enabled(feature)) client.options.useKey.setPressed(false);

        client.inGameHud.setOverlayMessage(
                Text.literal(feature + ": " + (enabled(feature) ? "ON" : "OFF")), false);
    }

    private boolean enabled(String feature) {
        return ENABLED.contains(feature);
    }

    private void equipTotem(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        for (int i = 0; i < 36; i++) {
            if (!player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int handlerSlot = i < 9 ? i + 36 : i;
            client.interactionManager.clickSlot(
                    player.playerScreenHandler.syncId, handlerSlot, 40, SlotActionType.SWAP, player);
            return;
        }
    }

    private void handleAutoCrystal(MinecraftClient client) {
        PlayerEntity player = client.player;
        PlayerEntity target = findTarget(client, 5.0);
        if (player == null || target == null) return;

        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof EndCrystalEntity
                    && entity.squaredDistanceTo(target) <= 16.0
                    && entity.squaredDistanceTo(player) <= 25.0) {
                client.interactionManager.attackEntity(player, entity);
                player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }

        int slot = findHotbarItem(player, Items.END_CRYSTAL);
        if (slot < 0) return;
        player.getInventory().selectedSlot = slot;
        BlockPos targetPos = target.getBlockPos();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos base = targetPos.add(dx, -1, dz);
                BlockState state = client.world.getBlockState(base);
                if (!(state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))) continue;
                if (!client.world.getBlockState(base.up()).isAir()
                        || !client.world.getBlockState(base.up(2)).isAir()) continue;
                if (player.squaredDistanceTo(Vec3d.ofCenter(base)) > 25.0) continue;
                BlockHitResult hit = new BlockHitResult(
                        Vec3d.ofCenter(base).add(0.0, 0.5, 0.0), Direction.UP, base, false);
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
    }

    private void handleAutoAnchor(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        if (client.world.getDimension().respawnAnchorWorks()) {
            anchorStage = 0;
            anchorPosition = null;
            return;
        }

        if (anchorStage > 0 && ++anchorTimeout > 20) {
            anchorStage = 0;
            anchorPosition = null;
            anchorTimeout = 0;
        }

        if (anchorStage == 0) {
            PlayerEntity target = findTarget(client, 4.5);
            int anchorSlot = findHotbarItem(player, Items.RESPAWN_ANCHOR);
            if (target == null || anchorSlot < 0) return;
            player.getInventory().selectedSlot = anchorSlot;
            BlockPos targetPos = target.getBlockPos();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos place = targetPos.add(dx, 0, dz);
                    BlockPos support = place.down();
                    if (!client.world.getBlockState(place).isAir()
                            || !client.world.getBlockState(place.up()).isAir()
                            || !client.world.getBlockState(support).isSolidBlock(client.world, support)) continue;
                    if (player.squaredDistanceTo(Vec3d.ofCenter(place)) > 25.0) continue;
                    BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofCenter(support).add(0.0, 0.5, 0.0), Direction.UP, support, false);
                    client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                    player.swingHand(Hand.MAIN_HAND);
                    anchorPosition = place;
                    anchorStage = 1;
                    anchorTimeout = 0;
                    return;
                }
            }
        } else if (anchorPosition != null
                && client.world.getBlockState(anchorPosition).isOf(Blocks.RESPAWN_ANCHOR)) {
            if (anchorStage == 1) {
                int glowstoneSlot = findHotbarItem(player, Items.GLOWSTONE);
                if (glowstoneSlot < 0) {
                    anchorStage = 0;
                    anchorPosition = null;
                    return;
                }
                player.getInventory().selectedSlot = glowstoneSlot;
                interactWithBlock(client, anchorPosition);
                anchorStage = 2;
                anchorTimeout = 0;
            } else if (anchorStage == 2) {
                int otherSlot = findHotbarNonItem(player, Items.GLOWSTONE, Items.RESPAWN_ANCHOR);
                if (otherSlot < 0) {
                    anchorStage = 0;
                    anchorPosition = null;
                    return;
                }
                player.getInventory().selectedSlot = otherSlot;
                interactWithBlock(client, anchorPosition);
                anchorStage = 0;
                anchorPosition = null;
                anchorTimeout = 0;
            }
        }
    }

    private void interactWithBlock(MinecraftClient client, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private PlayerEntity findTarget(MinecraftClient client, double maxDistance) {
        PlayerEntity best = null;
        double bestDistance = maxDistance * maxDistance;
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == client.player || !candidate.isAlive() || candidate.isSpectator()) continue;
            double distance = client.player.squaredDistanceTo(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private int findHotbarItem(PlayerEntity player, Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private int findHotbarNonItem(PlayerEntity player, Item excludedOne, Item excludedTwo) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && !stack.isOf(excludedOne) && !stack.isOf(excludedTwo)) return i;
        }
        return -1;
    }

    private void handleAutoEat(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player.getHungerManager().getFoodLevel() > 15 || player.isUsingItem()) {
            if (player.getHungerManager().getFoodLevel() >= 20) client.options.useKey.setPressed(false);
            return;
        }
        int foodSlot = findFoodSlot(player);
        if (foodSlot >= 0) {
            player.getInventory().selectedSlot = foodSlot;
            client.options.useKey.setPressed(true);
        } else {
            client.options.useKey.setPressed(false);
        }
    }

    private int findFoodSlot(PlayerEntity player) {
        Item[] foods = {
                Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON,
                Items.COOKED_CHICKEN, Items.COOKED_RABBIT, Items.COOKED_COD,
                Items.COOKED_SALMON, Items.BREAD, Items.BAKED_POTATO,
                Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.APPLE,
                Items.CARROT, Items.POTATO, Items.BEETROOT, Items.DRIED_KELP
        };
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            for (Item food : foods) if (stack.isOf(food)) return slot;
        }
        return -1;
    }

    private void selectBestTool(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !client.options.attackKey.isPressed()) return;

        BlockState state = client.world.getBlockState(hit.getBlockPos());
        float bestSpeed = 1.0F;
        int bestSlot = client.player.getInventory().selectedSlot;
        for (int slot = 0; slot < 9; slot++) {
            float speed = client.player.getInventory().getStack(slot).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        client.player.getInventory().selectedSlot = bestSlot;
    }

    private void renderEntities(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (!enabled("ESP") && !enabled("TRACERS")) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d camera = context.camera().getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity == client.player || !entity.isAlive()) continue;
            if (client.player.squaredDistanceTo(entity) > 64.0 * 64.0) continue;
            boolean isPlayer = entity instanceof PlayerEntity;
            float red = isPlayer ? 0.1F : 1.0F;
            float green = isPlayer ? 0.8F : 0.2F;
            float blue = isPlayer ? 1.0F : 0.15F;

            if (enabled("ESP")) {
                Box box = entity.getBoundingBox().expand(0.035)
                        .offset(-camera.x, -camera.y, -camera.z);
                WorldRenderer.drawBox(matrices, lines, box, red, green, blue, 1.0F);
            }

            if (enabled("TRACERS")) {
                Vec3d end = entity.getPos().add(0.0, entity.getHeight() * 0.5, 0.0).subtract(camera);
                MatrixStack.Entry entry = matrices.peek();
                lines.vertex(entry, 0.0F, 0.0F, 0.0F).color(red, green, blue, 1.0F).normal(entry, 0.0F, 1.0F, 0.0F);
                lines.vertex(entry, (float) end.x, (float) end.y, (float) end.z).color(red, green, blue, 1.0F).normal(entry, 0.0F, 1.0F, 0.0F);
            }
        }
        immediate.draw(RenderLayer.getLines());
    }

    private void renderHud(DrawContext drawContext) {
        if (!enabled("HUD")) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        PlayerEntity player = client.player;
        String coordinates = String.format("XYZ: %.1f / %.1f / %.1f",
                player.getX(), player.getY(), player.getZ());
        drawContext.drawTextWithShadow(client.textRenderer, coordinates, 6, 6, 0xFFFFFF);
        drawContext.drawTextWithShadow(client.textRenderer,
                "Hack Minecraft | Totem:" + (enabled("AUTO_TOTEM") ? "ON" : "OFF")
                        + " Crystal:" + (enabled("AUTO_CRYSTAL") ? "ON" : "OFF")
                        + " Anchor:" + (enabled("AUTO_ANCHOR") ? "ON" : "OFF"),
                6, 18, 0x55FFFF);

        int line = 30;
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other == player) continue;
            if (line > 78) break;
            String radar = other.getName().getString() + " - "
                    + String.format("%.1fm", Math.sqrt(player.squaredDistanceTo(other)));
            drawContext.drawTextWithShadow(client.textRenderer, radar, 6, line, 0xFFAA55);
            line += 10;
        }
    }
}