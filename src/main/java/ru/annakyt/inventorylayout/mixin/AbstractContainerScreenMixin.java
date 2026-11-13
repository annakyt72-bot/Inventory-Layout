package ru.annakyt.inventorylayout.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
    @Shadow @Final protected T menu;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;

    @Shadow
    protected abstract <W extends GuiEventListener & Renderable & NarratableEntry> W addRenderableWidget(W widget);

    @Inject(method = "init", at = @At("TAIL"))
    private void inventoryLayout$addButton(CallbackInfo ci) {
        boolean playerInventoryScreen = Minecraft.getInstance().screen instanceof InventoryScreen;
        Component label = Component.literal(playerInventoryScreen ? "Сохр." : "Сорт.");
        Component hint = Component.literal(playerInventoryScreen
                ? "Сохранить расположение предметов"
                : "Разложить предметы по сохранённым слотам");

        Button button = Button.builder(label, pressed -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            Inventory inventory = minecraft.player.getInventory();

            if (playerInventoryScreen) {
                ru.annakyt.inventorylayout.InventoryLayoutClient.saveLayout(inventory);
            } else if (ru.annakyt.inventorylayout.InventoryLayoutClient.hasLayout()) {
                ru.annakyt.inventorylayout.InventoryLayoutClient.applyLayout(menu, inventory);
            }
        }).bounds(leftPos + imageWidth - 22, topPos + 4, 18, 18)
          .tooltip(Tooltip.create(hint))
          .build();

        addRenderableWidget(button);
    }
}
