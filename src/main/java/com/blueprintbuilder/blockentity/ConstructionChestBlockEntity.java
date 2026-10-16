package com.blueprintbuilder.blockentity;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.construction.ConstructionEngine;
import com.blueprintbuilder.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.blueprintbuilder.menu.ConstructionChestMenu;

public class ConstructionChestBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 27;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final BitSet placed = new BitSet();
    private final Map<String, Integer> consumed = new HashMap<>();
    private final ContainerData data = new ChestData();
    private @Nullable ResourceLocation blueprintId;
    private BlockPos anchor = BlockPos.ZERO;
    private int rotationOrdinal;
    private boolean reconciled;
    private boolean announcedComplete;
    private double accumulator;
    private int blockedCount;
    private int tickCounter;

    public ConstructionChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_CHEST.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConstructionChestBlockEntity chest) {
        if (level.isClientSide || chest.blueprintId == null) {
            return;
        }
        ConstructionEngine.tick(level, chest);
    }

    public void assign(ResourceLocation blueprintId, BlockPos anchor, int rotationOrdinal) {
        this.blueprintId = blueprintId;
        this.anchor = anchor.immutable();
        this.rotationOrdinal = rotationOrdinal & 3;
        this.placed.clear();
        this.consumed.clear();
        this.reconciled = false;
        this.announcedComplete = false;
        this.accumulator = 0;
        sync();
    }

    public @Nullable ResourceLocation blueprintId() {
        return blueprintId;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public int rotationOrdinal() {
        return rotationOrdinal;
    }

    public BitSet placed() {
        return placed;
    }

    public int consumed(String itemId) {
        return consumed.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> consumedView() {
        return Map.copyOf(consumed);
    }

    public void addConsumed(String itemId) {
        consumed.merge(itemId, 1, Integer::sum);
    }

    public ContainerData data() {
        return data;
    }

    public int blockedCount() {
        return blockedCount;
    }

    public void setBlockedCount(int blockedCount) {
        this.blockedCount = blockedCount;
    }

    public double accumulator() {
        return accumulator;
    }

    public void setAccumulator(double accumulator) {
        this.accumulator = accumulator;
    }

    public boolean reconciled() {
        return reconciled;
    }

    public void setReconciled(boolean reconciled) {
        this.reconciled = reconciled;
    }

    public boolean announcedComplete() {
        return announcedComplete;
    }

    public void setAnnouncedComplete(boolean announcedComplete) {
        this.announcedComplete = announcedComplete;
    }

    public int tickCounter() {
        return tickCounter;
    }

    public void setTickCounter(int tickCounter) {
        this.tickCounter = tickCounter;
    }

    public Set<String> availableItems() {
        Set<String> available = new HashSet<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                available.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return available;
    }

    public boolean consumeOne(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return false;
        }
        for (ItemStack stack : items) {
            if (stack.is(item)) {
                stack.shrink(1);
                addConsumed(itemId);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeContents(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readContents(tag, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, items, registries);
        if (blueprintId != null) {
            tag.putString("Blueprint", blueprintId.toString());
        }
        tag.putLong("Anchor", anchor.asLong());
        tag.putInt("Rotation", rotationOrdinal);
        tag.putByteArray("Placed", placed.toByteArray());
        tag.putBoolean("Announced", announcedComplete);
        ListTag consumedList = new ListTag();
        for (var entry : consumed.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("id", entry.getKey());
            item.putInt("count", entry.getValue());
            consumedList.add(item);
        }
        tag.put("Consumed", consumedList);
    }

    private void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        blueprintId = tag.contains("Blueprint", Tag.TAG_STRING) ? ResourceLocation.parse(tag.getString("Blueprint")) : null;
        anchor = tag.contains("Anchor", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Anchor")) : BlockPos.ZERO;
        rotationOrdinal = tag.getInt("Rotation") & 3;
        placed.clear();
        if (tag.contains("Placed", Tag.TAG_BYTE_ARRAY)) {
            placed.or(BitSet.valueOf(tag.getByteArray("Placed")));
        }
        announcedComplete = tag.getBoolean("Announced");
        consumed.clear();
        ListTag consumedList = tag.getList("Consumed", Tag.TAG_COMPOUND);
        for (int i = 0; i < consumedList.size(); i++) {
            CompoundTag item = consumedList.getCompound(i);
            consumed.put(item.getString("id"), item.getInt("count"));
        }
        reconciled = false;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[items.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintbuilder.construction_chest");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ConstructionChestMenu(containerId, inventory, this, data);
    }

    private final class ChestData implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> placed.cardinality();
                case 1 -> ConstructionEngine.totalSteps(ConstructionChestBlockEntity.this);
                case 2 -> blockedCount;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 2) {
                blockedCount = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
