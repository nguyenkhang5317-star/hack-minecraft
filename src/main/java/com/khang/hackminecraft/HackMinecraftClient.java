package com.khang.hackminecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class HackMinecraftClient implements ClientModInitializer {
    private static final Map<String, KeyBinding> TOGGLES = new LinkedHashMap<>();
    private static final Set<String> ENABLED = new HashSet<>();
    private double savedGamma = 1.0;
    private int totemCooldown;
    private int antiAfkCooldown;

    @Override
    public void onInitializeClient() {
        registerToggle("AUTO_TOTEM", GLFW.GLFW_KEY_P);
        registerToggle("FULLBRIGHT", GLFW.GLFW_KEY_L);
        registerToggle("AUTO_SPRINT", GLFW.GLFW_KEY_G);
        registerToggle("AUTO_EAT", GLFW.GLFW_KEY_H);
        registerToggle("AUTO_TOOL", GLFW.GLFW_KEY_I);
        registerToggle("AUTO_RESPAWN", GLFW.GLFW_KEY_R);
        registerToggle("COORDS_HUD", GLFW.GLFW_KEY_U);
        registerToggle("DURABILITY_HUD", GLFW.GLFW_KEY_D);
        registerToggle("PERFORMANCE_HUD", GLFW.GLFW_KEY_F);
        registerToggle("ANTI_AFK", GLFW.GLFW_KEY_Y);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
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

        if (enabled("AUTO_RESPAWN") && client.player.isDead()) {
            client.player.requestRespawn();
        }

        if (enabled("ANTI_AFK") && antiAfkCooldown == 0) {
            if (client.player.isOnGround() && !client.player.isUsingItem()) client.player.jump();
            antiAfkCooldown = 1200;
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

        if (feature.equals("AUTO_EAT") && !enabled(feature)) {
            client.options.useKey.setPressed(false);
        }

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

    private void handleAutoEat(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player.getHungerManager().getFoodLevel() > 15) {
            client.options.useKey.setPressed(false);
            return;
        }
        if (player.isUsingItem()) return;

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

        var state = client.world.getBlockState(hit.getBlockPos());
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

    private void renderHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int y = 6;
        if (enabled("COORDS_HUD")) {
            PlayerEntity player = client.player;
            String coordinates = String.format("XYZ: %.1f / %.1f / %.1f",
                    player.getX(), player.getY(), player.getZ());
            drawContext.drawTextWithShadow(client.textRenderer, coordinates, 6, y, 0xFFFFFF);
            y += 12;
            long time = client.world.getTimeOfDay() % 24000L;
            drawContext.drawTextWithShadow(client.textRenderer, "World time: " + time, 6, y, 0xFFFFFF);
            y += 12;
        }

        if (enabled("DURABILITY_HUD")) {
            for (ItemStack stack : client.player.getArmorItems()) {
                if (stack.isEmpty() || !stack.isDamageable()) continue;
                int remaining = stack.getMaxDamage() - stack.getDamage();
                int percent = Math.max(0, remaining * 100 / stack.getMaxDamage());
                drawContext.drawTextWithShadow(client.textRenderer,
                        stack.getName().getString() + ": " + percent + "%",
                        6, y, percent <= 20 ? 0xFF5555 : 0x55FF55);
                y += 12;
            }
        }

        if (enabled("PERFORMANCE_HUD")) {
            drawContext.drawTextWithShadow(client.textRenderer,
                    "FPS: " + client.getCurrentFps(), 6, y, 0x55FFFF);
            y += 12;
            drawContext.drawTextWithShadow(client.textRenderer,
                    "Food: " + client.player.getHungerManager().getFoodLevel()
                            + " | Health: " + String.format("%.1f", client.player.getHealth()),
                    6, y, 0x55FFFF);
        }
    }
}