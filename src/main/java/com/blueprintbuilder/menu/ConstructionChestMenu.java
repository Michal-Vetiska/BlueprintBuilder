package com.blueprintbuilder.menu;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.blockentity.ConstructionChestBlockEntity;
import com.blueprintbuilder.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ConstructionChestMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final @Nullable ConstructionChestBlockEntity chest;

    public ConstructionChestMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, findChest(playerInventory, buffer.readBlockPos()));
    }

    private ConstructionChestMenu(int containerId, Inventory playerInventory, @Nullable ConstructionChestBlockEntity chest) {
        this(containerId, playerInventory, chest == null ? new SimpleContainer(ConstructionChestBlockEntity.SLOT_COUNT) : chest,
                chest == null ? new SimpleContainerData(3) : chest.data(), chest);
    }

    public ConstructionChestMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        this(containerId, playerInventory, container, data, container instanceof ConstructionChestBlockEntity chest ? chest : null);
    }

    private ConstructionChestMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, @Nullable ConstructionChestBlockEntity chest) {
        super(ModMenus.CONSTRUCTION_CHEST.get(), containerId);
        this.container = container;
        this.data = data;
        this.chest = chest;
        checkContainerSize(container, ConstructionChestBlockEntity.SLOT_COUNT);
        addDataSlots(data);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int inventoryTop = 84;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, inventoryTop + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, inventoryTop + 58));
        }
    }

    private static @Nullable ConstructionChestBlockEntity findChest(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        return blockEntity instanceof ConstructionChestBlockEntity chest ? chest : null;
    }

    public @Nullable ConstructionChestBlockEntity chest() {
        return chest;
    }

    public int placed() {
        return data.get(0);
    }

    public int total() {
        return data.get(1);
    }

    public int blocked() {
        return data.get(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            if (index < ConstructionChestBlockEntity.SLOT_COUNT) {
                if (!moveItemStackTo(stack, ConstructionChestBlockEntity.SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, ConstructionChestBlockEntity.SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
