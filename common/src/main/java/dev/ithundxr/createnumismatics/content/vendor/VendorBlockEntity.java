package dev.ithundxr.createnumismatics.content.vendor;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.Couple;
import com.simibubi.create.foundation.utility.*;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.backend.Coin;
import dev.ithundxr.createnumismatics.content.backend.Trusted;
import dev.ithundxr.createnumismatics.content.backend.behaviours.SliderStylePriceBehaviour;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListContainer;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListHolder;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListMenu;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListMenu.TrustListSham;
import dev.ithundxr.createnumismatics.content.bank.CardItem;
import dev.ithundxr.createnumismatics.content.coins.DiscreteCoinBag;
import dev.ithundxr.createnumismatics.registry.NumismaticsBlocks;
import dev.ithundxr.createnumismatics.registry.NumismaticsMenuTypes;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import dev.ithundxr.createnumismatics.util.ItemUtil;
import dev.ithundxr.createnumismatics.util.TextUtils;
import dev.ithundxr.createnumismatics.util.UsernameUtils;
import dev.ithundxr.createnumismatics.util.Utils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VendorBlockEntity extends SmartBlockEntity implements Trusted, TrustListHolder, IHaveGoggleInformation, WorldlyContainer, MenuProvider {
    public final Container cardContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            VendorBlockEntity.this.setChanged();
        }
    };

    public final Container sellingContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            VendorBlockEntity.this.setChanged();
        }
    };


    @Nullable
    protected UUID owner;
    protected final List<UUID> trustList = new ArrayList<>();
    public final TrustListContainer trustListContainer = new TrustListContainer(trustList, this::setChanged);

    protected final DiscreteCoinBag inventory = new DiscreteCoinBag();
    private boolean delayedDataSync = false;

    private SliderStylePriceBehaviour price;
    public final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    @SuppressWarnings("FieldCanBeLocal")
    private ScrollOptionBehaviour<TrustListSham> trustListButton;



    public VendorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        trustListButton = TrustListMenu.makeConfigureButton(this, new VendorValueBoxTransform(), isCreativeVendor()
            ? NumismaticsBlocks.CREATIVE_VENDOR.asStack()
            : NumismaticsBlocks.VENDOR.asStack());
        behaviours.add(trustListButton);

        price = new SliderStylePriceBehaviour(this, this::addCoin);
        behaviours.add(price);
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (owner != null)
            tag.putUUID("Owner", owner);

        if (!inventory.isEmpty()) {
            tag.put("Inventory", inventory.save(new CompoundTag()));
        }

        if (!cardContainer.getItem(0).isEmpty()) {
            tag.put("Card", cardContainer.getItem(0).save(new CompoundTag()));
        }

        if (!getSellingItem().isEmpty()) {
            tag.put("Selling", getSellingItem().save(new CompoundTag()));
        }

        if (!trustListContainer.isEmpty()) {
            tag.put("TrustListInv", trustListContainer.save(new CompoundTag()));
        }

        if (!items.isEmpty()) {
            tag.put("Inventory", ContainerHelper.saveAllItems(new CompoundTag(), items));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;

        inventory.clear();
        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            inventory.load(tag.getCompound("Inventory"));
        }

        if (tag.contains("Card", Tag.TAG_COMPOUND)) {
            ItemStack cardStack = ItemStack.of(tag.getCompound("Card"));
            cardContainer.setItem(0, cardStack);
        } else {
            cardContainer.setItem(0, ItemStack.EMPTY);
        }

        if (tag.contains("Selling", Tag.TAG_COMPOUND)) {
            ItemStack sellingStack = ItemStack.of(tag.getCompound("Selling"));
            sellingContainer.setItem(0, sellingStack);
        } else {
            sellingContainer.setItem(0, ItemStack.EMPTY);
        }

        trustListContainer.clearContent();
        trustList.clear();
        if (tag.contains("TrustListInv", Tag.TAG_COMPOUND)) {
            trustListContainer.load(tag.getCompound("TrustListInv"));
        }

        items.clear();
        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound("Inventory"), items);
        }
    }

    @Nullable
    private Boolean isCreativeVendorCached;

    public boolean isCreativeVendor() {
        if (isCreativeVendorCached == null)
            isCreativeVendorCached = getBlockState().getBlock() instanceof VendorBlock vendorBlock && vendorBlock.isCreativeVendor;
        return isCreativeVendorCached;
    }

    @Override
    public boolean isTrustedInternal(Player player) {
        if (Utils.isDevEnv()) { // easier to test this way in dev
            return player.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS);
        } else {
            if (isCreativeVendor()) {
                return player != null && player.isCreative();
            }

            return owner == null || owner.equals(player.getUUID()) || trustList.contains(player.getUUID());
        }
    }

    public void addCoin(Coin coin, int count) {
        UUID depositAccount = getDepositAccount();
        if (depositAccount != null) {
            BankAccount account = Numismatics.BANK.getAccount(depositAccount);
            if (account != null) {
                account.deposit(coin, count);
                return;
            }
        }
        inventory.add(coin, count);
        setChanged();
    }

    @Nullable
    public UUID getDepositAccount() {
        ItemStack cardStack = cardContainer.getItem(0);
        if (cardStack.isEmpty())
            return null;
        if (!NumismaticsTags.AllItemTags.CARDS.matches(cardStack))
            return null;

        return CardItem.get(cardStack);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide)
            return;
        if (delayedDataSync) {
            delayedDataSync = false;
            sendData();
        }
        UUID depositAccount = getDepositAccount();
        if (depositAccount != null && !inventory.isEmpty()) {
            BankAccount account = Numismatics.BANK.getAccount(depositAccount);
            if (account != null) {
                for (Coin coin : Coin.values()) {
                    int count = inventory.getDiscrete(coin);
                    inventory.subtract(coin, count);
                    account.deposit(coin, count);
                }
            }
        }
    }

    void notifyDelayedDataSync() {
        delayedDataSync = true;
    }

    @Override
    public ImmutableList<UUID> getTrustList() {
        return ImmutableList.copyOf(trustList);
    }

    @Override
    public Container getTrustListBackingContainer() {
        return trustListContainer;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ItemStack sellingStack = getSellingItem();
        if (sellingStack.isEmpty())
            return false;

        Couple<Integer> cogsAndSpurs = Coin.COG.convert(getTotalPrice());
        int cogs = cogsAndSpurs.getFirst();
        int spurs = cogsAndSpurs.getSecond();
        MutableComponent balanceLabel = Components.translatable("block.numismatics.brass_depositor.tooltip.price",
            TextUtils.formatInt(cogs), Coin.COG.getName(cogs), spurs);

        boolean isFirst = true;
        for (Component component : Screen.getTooltipFromItem(Minecraft.getInstance(), sellingStack)) {
            MutableComponent mutable = component.copy();
            if (isFirst) {
                isFirst = false;
                if (sellingStack.getCount() != 1) {
                    mutable.append(
                        Components.translatable("gui.numismatics.vendor.count", sellingStack.getCount())
                            .withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            Lang.builder()
                .add(mutable)
                .forGoggles(tooltip);
        }

        tooltip.add(Components.immutableEmpty());

        Lang.builder()
            .add(balanceLabel.withStyle(Coin.closest(getTotalPrice()).rarity.color))
            .forGoggles(tooltip);
        return true;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Components.translatable(isCreativeVendor() ? "block.numismatics.creative_vendor" : "block.numismatics.vendor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, @NotNull Inventory inventory, @NotNull Player player) {
        if (!isTrusted(player))
            return null;
        return new VendorMenu(NumismaticsMenuTypes.VENDOR.get(), i, inventory, this);
    }

    public int getTotalPrice() {
        return price.getTotalPrice();
    }

    public int getPrice(Coin coin) {
        return price.getPrice(coin);
    }

    public void setPrice(Coin coin, int price) {
        this.price.setPrice(coin, price);
    }

    public ItemStack getSellingItem() {
        return sellingContainer.getItem(0);
    }

    /* Begin Container */

    @Override
    public int @NotNull [] getSlotsForFace(@NotNull Direction side) {
        return side == Direction.DOWN ? new int[0] : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    @Override
    public boolean canPlaceItem(int index, @NotNull ItemStack stack) {
        return matchesSellingItem(stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, @NotNull ItemStack itemStack, @Nullable Direction direction) {
        return direction != Direction.DOWN && canPlaceItem(index, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, @NotNull ItemStack stack, @NotNull Direction direction) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemStack.isEmpty()) {
            setChanged();
        }

        return itemStack;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack itemStack = items.get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            items.set(slot, ItemStack.EMPTY);
            return itemStack;
        }
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /* End Container */

    protected static class VendorValueBoxTransform extends CenteredSideValueBoxTransform {
        public VendorValueBoxTransform() {
            super((state, d) -> d == state.getValue(VendorBlock.HORIZONTAL_FACING));
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 4, 15.5);
        }
    }

    @NotNull
    @Contract("_ -> new")
    private CompoundTag cleanTags(@NotNull CompoundTag tag) {
        tag = tag.copy();
        tag.remove("RepairCost");
        tag.remove("Count");

        // sort enchants
        ListTag enchants = tag.getList("Enchantments", Tag.TAG_COMPOUND);
        if (!enchants.isEmpty()) {
            ArrayList<Tag> tags = new ArrayList<>(enchants);
            tags.sort((a, b) -> {
                if (a.equals(b))
                    return 0;
                if (a instanceof CompoundTag ca && b instanceof CompoundTag cb) {
                    if (ca.contains("id", Tag.TAG_STRING) && cb.contains("id", Tag.TAG_STRING)) {
                        int comp = ca.getString("id").compareTo(cb.getString("id"));
                        if (comp != 0) return comp;
                    }

                    return ca.getShort("lvl") - cb.getShort("lvl");
                }
                return 0;
            });

            enchants = new ListTag();
            enchants.addAll(tags);
            tag.put("Enchantments", enchants);
        }

        return tag;
    }

    public boolean matchesSellingItem(@NotNull ItemStack b) {
        ItemStack a = getSellingItem();
        if (a.isEmpty() || b.isEmpty())
            return false;

        if (!ItemStack.isSameItem(a, b))
            return false;

        CompoundTag an = a.getTag();
        CompoundTag bn = b.getTag();

        if (an == null || bn == null) {
            return an == bn;
        }

        an = cleanTags(an);
        bn = cleanTags(bn);

        return an.equals(bn);
    }

    protected void condenseItems() {
        NonNullList<ItemStack> newItems = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            newItems.set(i, items.get(i));
        }
        items.clear();

        for (ItemStack stack : newItems) {
            ItemUtil.moveItemStackTo(stack, this, false);
        }
    }

    public void tryBuy(Player player, InteractionHand hand) {
        // condense stock
        // (try to) charge cost
        // dispense stock
        ItemStack selling = getSellingItem();
        if (selling.isEmpty())
            return;

        condenseItems();

        if (isCreativeVendor()) {
            if (price.deduct(player, hand)) {
                ItemStack output = selling.copy();
                ItemUtil.givePlayerItem(player, output);
            } else {
                // insufficient funds
                player.displayClientMessage(Components.translatable("gui.numismatics.vendor.insufficient_funds")
                    .withStyle(ChatFormatting.DARK_RED), false);
            }
            return;
        } else {
            for (ItemStack stack : items) {
                if (matchesSellingItem(stack) && stack.getCount() >= selling.getCount()) {
                    if (price.deduct(player, hand)) {
                        ItemStack output = stack.split(selling.getCount());
                        ItemUtil.givePlayerItem(player, output);
                    } else {
                        // insufficient funds
                        player.displayClientMessage(Components.translatable("gui.numismatics.vendor.insufficient_funds")
                            .withStyle(ChatFormatting.DARK_RED), false);
                    }
                    return;
                }
            }

            // out of stock
            String ownerName = UsernameUtils.INSTANCE.getName(owner, null);
            if (ownerName != null) {
                player.displayClientMessage(Components.translatable("gui.numismatics.vendor.out_of_stock.named", ownerName)
                    .withStyle(ChatFormatting.DARK_RED), false);
            } else {
                player.displayClientMessage(Components.translatable("gui.numismatics.vendor.out_of_stock")
                    .withStyle(ChatFormatting.DARK_RED), false);
            }
        }
    }
}
