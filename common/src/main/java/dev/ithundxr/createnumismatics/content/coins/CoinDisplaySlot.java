package dev.ithundxr.createnumismatics.content.coins;

import com.mojang.datafixers.util.Pair;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.Coin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CoinDisplaySlot extends Slot {
    private static final Container emptyInventory = new SimpleContainer(0);
    private final Coin coin;
    private final boolean canInsert = false;
    private final boolean canExtract = false;

    public CoinDisplaySlot(Coin coin, int x, int y) {
        super(emptyInventory, 0, x, y);
        this.coin = coin;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public @NotNull ItemStack getItem() {
        return coin.asStack(1);
    }

    @Override
    public void set(@NotNull ItemStack stack) {}

    @Override
    public void onQuickCraft(@NotNull ItemStack oldStackIn, @NotNull ItemStack newStackIn) {}

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return getMaxStackSize();
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return false;
    }

    @Override
    public @NotNull ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull Optional<ItemStack> tryRemove(int count, int decrement, @NotNull Player player) {
        return Optional.empty();
    }

    @Nullable
    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return Pair.of(InventoryMenu.BLOCK_ATLAS, Numismatics.asResource("item/coin/outline/"+coin.getName()));
    }
}
