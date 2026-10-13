package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.interfaces.MapPrinter;
import com.julflips.nerv_printer.utils.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SuppressionPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMining = settings.createGroup("Mining & Collecting", false);
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range")
        .description("The maximum range you can place blocks around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many milliseconds to wait after placing.")
        .defaultValue(50)
        .min(1)
        .sliderRange(10, 300)
        .build()
    );

    private final Setting<List<Block>> startBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-blocks")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<Block> fillerBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("filler-block")
        .description("The block to use as a filler.")
        .defaultValue(Blocks.RESIN_BLOCK)
        .build()
    );


    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.Off)
        .build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Disable if the bot should continue after reconnecting to the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotatePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place")
        .description("Rotate when placing a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> supportBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("support-blocks")
        .description("Place blocks with support. Can be disabled if there is a 2 block heigh ceiling or airplace allowed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> customFolderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-folder-path")
        .description("Allows to set a custom path to the nbt folder.")
        .defaultValue(false)
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("nerv-printer-folder-path")
        .description("The path to your nerv-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\nerv-printer")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> customFolderPath.get())
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    private final Setting<Boolean> useDefaultConfigFile = sgGeneral.add(new BoolSetting.Builder()
        .name("use-default-config-file")
        .description("Load a config file when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> configFileName = sgGeneral.add(new StringSetting.Builder()
        .name("config-file-name")
        .description("The config file that is loaded  when the module is enabled.")
        .defaultValue("suppressed-config.json")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> useDefaultConfigFile.get())
        .build()
    );

    // Mining & Collecting

    private final Setting<Double> minMiningRange = sgMining.add(new DoubleSetting.Builder()
        .name("min-mining-range")
        .description("The minimum distance to keep to blocks when mining.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(0.5, 3.5)
        .build()
    );

    private final Setting<Double> durabilityBuffer = sgMining.add(new DoubleSetting.Builder()
        .name("durability-buffer")
        .description("The additional required durability for restocked mining tools on top of the predicted one (in %).")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> minDurability = sgMining.add(new IntSetting.Builder()
        .name("min-durability")
        .description("The minimum absolute durability value allowed on a mining tools before exchanging.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Integer> masterHelpMinLines = sgMining.add(new IntSetting.Builder()
        .name("master-help-min-lines")
        .description("The minimum lines left to mine required so the master decides to join mining after locking the map.")
        .defaultValue(4)
        .min(1)
        .max(64)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> collectBlocks = sgMining.add(new BoolSetting.Builder()
        .name("collect-mined-blocks")
        .description("Collect the dropped block items again while mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> collectAllDirections = sgMining.add(new BoolSetting.Builder()
        .name("collect-both-directions")
        .description("If set to false, only collect items when going south-to-north and ignore them otherwise. " +
                    "This could lead to a speedup at the risk of despawning items.")
        .defaultValue(true)
        .visible(() -> collectBlocks.get())
        .build()
    );

    private final Setting<Integer> runsBetweenDumps = sgMining.add(new IntSetting.Builder()
        .name("runs-between-dumps")
        .description("How many times to mine a line back and forth before dumping.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Double> minZCollectionDistance = sgMining.add(new DoubleSetting.Builder()
        .name("min-z-collection-distance")
        .description("The Z distance to the mined & dropped items required to start collecting it.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 2)
        .visible(() -> collectBlocks.get())
        .build()
    );

    // Advanced

    private final Setting<Integer> preRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-restock-delay")
        .description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> invActionDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("inventory-action-delay")
        .description("How many ticks to wait between each inventory action (moving a stack).")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> postRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-restock-delay")
        .description("How many ticks to wait after restocking.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> preSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-swap-delay")
        .description("How many ticks to wait before swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> postSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-swap-delay")
        .description("How many ticks to wait after swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> retryInteractTimer = sgAdvanced.add(new IntSetting.Builder()
        .name("retry-interact-timer")
        .description("How many ticks to wait for chest response before interacting with it again.")
        .defaultValue(80)
        .min(1)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> mapLoadTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("map-load-timeout")
        .description("How many ticks to wait to update the map in hand.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> posResetTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("pos-reset-timeout")
        .description("How many ticks to wait after the player position was reset by the server.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

//    private final Setting<Boolean> fixErrors = sgAdvanced.add(new BoolSetting.Builder()
//        .name("fix-errors")
//        .description("Fix errors by mining the misplaced blocks on the fly.")
//        .defaultValue(false)
//        .build()
//    );

    private final Setting<Boolean> snapToCheckpoints = sgAdvanced.add(new BoolSetting.Builder()
        .name("snap-to-checkpoints")
        .description("Snap to checkpoints when getting close.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgAdvanced.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the nerv-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgAdvanced.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugPrints = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug-prints")
        .description("Prints additional information.")
        .defaultValue(false)
        .build()
    );

    // Multi User

    private final Setting<String> tcpIp = sgMultiUser.add(new StringSetting.Builder()
        .name("TCP-IP")
        .description("The loopback IP used by bot-to-bot TCP communication.")
        .defaultValue("127.0.0.1")
        .onChanged(value -> updateTcpAddress())
        .build()
    );

    private final Setting<Integer> tcpPort = sgMultiUser.add(new IntSetting.Builder()
        .name("TCP-port")
        .description("The loopback TCP port used by bot-to-bot communication.")
        .defaultValue(42069)
        .min(1)
        .max(65535)
        .sliderRange(1, 65535)
        .onChanged(value -> updateTcpAddress())
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderMap = sgRender.add(new BoolSetting.Builder()
        .name("render-map")
        .description("Highlights the position of the map blocks.")
        .defaultValue(false)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderNextBlockPos = sgRender.add(new BoolSetting.Builder()
        .name("render-next-block-pos")
        .description("Highlights the block position of the current place/mining action.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderChestPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-chest-positions")
        .description("Highlights the selected chests.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderOpenPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-open-positions")
        .description("Indicate the position the bot will go to in order to interact with the chest.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderCheckpoints = sgRender.add(new BoolSetting.Builder()
        .name("render-checkpoints")
        .description("Indicate the checkpoints the bot will traverse.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderSpecialInteractions = sgRender.add(new BoolSetting.Builder()
        .name("render-special-interactions")
        .description("Indicate the position where the reset button and cartography table will be used.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Double> indicatorSize = sgRender.add(new DoubleSetting.Builder()
        .name("indicator-size")
        .description("How big the rendered indicator will be.")
        .defaultValue(0.13)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The render color.")
        .defaultValue(new SettingColor(22, 230, 206, 155))
        .visible(() -> render.get())
        .build()
    );

    int timeoutTicks;
    int interactTimeout;
    int toBeSwappedSlot;
    int lastPlacedLine;
    int minedLines;
    int suppressedLines;
    int lastReportedZ;
    int awaitSuppressionZ;
    int runsUntilDump;
    long lastTickTime;
    boolean closeNextInvPacket;
    boolean workOnUpper;
    boolean masterMiningReady;
    State state;
    State oldState;
    State debugPreviousState;
    Tuple<Integer, Integer> workingInterval;                         // Interval the bot should work in 0-127
    Tuple<BlockPos, Vec3> cartographyTable;
    Tuple<BlockPos, Vec3> finishedMapChest;
    Tuple<BlockPos, Vec3> usedToolChest;
    ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests;
    ArrayList<Tuple<Vec3, Tuple<Float, Float>>> dumpStations;                    // Pos, Yaw, Pitch
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    BlockPos nextBlockPos;
    Item lastSwappedMaterial;
    ClientboundContainerSetContentPacket toBeHandledInvPacket;
    HashMap<Integer, Tuple<Block, Integer>> blockPaletteDict;        // Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict;   // Maps block to the chest pos and the open position
    Set<ItemStack> toolSet;                                         // Set of all registered tool item stacks
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Integer> lowerMiningCommand;
    ArrayList<Integer> upperMiningCommand;
    ArrayList<Triple<Item, Integer, Integer>> restockList;          // Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Tuple<Vec3, Tuple<String, BlockPos>>> checkpoints;     // (GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<String> switchedToMiningSlaves;
    File mapFolder;
    File mapFile;
    BlockPos lowerMapCorner;
    BlockPos upperMapCorner;
    Block[][][] lowerMapLayer;
    Block[][][] upperMapLayer;

    public SuppressionPrinter() {
        super(Addon.CATEGORY, "suppression-printer", "Uses inplace suppression to build fullblock staircased maps.");
    }

    @Override
    public void onActivate() {
        lastTickTime = System.currentTimeMillis();
        if (!activationReset.get() && checkpoints != null) {
            return;
        }
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        toolSet = new HashSet<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        lowerMiningCommand = new ArrayList<>();
        upperMiningCommand = new ArrayList<>();
        switchedToMiningSlaves = new ArrayList<>();
        lowerMapCorner = null;
        upperMapCorner = null;
        lastInteractedChest = null;
        nextBlockPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        usedToolChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStations = new ArrayList<>();
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        workOnUpper = false;
        masterMiningReady = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        toBeSwappedSlot = -1;
        minedLines = 128;
        suppressedLines = -1;
        lastReportedZ = -1;
        runsUntilDump = runsBetweenDumps.get();
        oldState = null;
        debugPreviousState = null;
        lowerMapLayer = new Block[128][129][2];
        upperMapLayer = new Block[128][129][2];

        setInterval(new Tuple<>(0, 127));
        // Initialize Slave System settings
        SlaveSystem.setupSlaveSystem(this, tcpIp.get(), tcpPort.get());

        if (!customFolderPath.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory(), "nerv-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createFolders(mapFolder)) {
            toggle();
            return;
        }

        if (!prepareNextMapFile()) return;
        info("§aBuilding: §b" + mapFile.getName());

        state = State.SelectingLowerMapArea;
        if (useDefaultConfigFile.get()) {
            File configFolder = new File(mapFolder, "_configs");
            if (!loadConfig(new File(configFolder, configFileName.get()))) {
                info("§aSelect the §bLower Map Building Area (128x128)§a. (Right-click anywhere on the floor)");
            }
        } else {
            info("§aSelect the §bLower Map Building Area (128x128)§a. (Right-click anywhere on the floor)");
        }
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof ServerboundPlayerActionPacket packet
            && packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM) {
            dumpStations.add(new Tuple<>(mc.player.position(), new Tuple<>(mc.player.getYRot(), mc.player.getXRot())));
            info("§aDump Station selected. You can register more or continue with the §bCartography Table.");
            return;
        }
        if (!(event.packet instanceof ServerboundUseItemOnPacket packet) || state == null) return;
        switch (state) {
            case SelectingLowerMapArea:
                BlockPos hitPos = packet.getHitResult().getBlockPos().relative(packet.getHitResult().getDirection());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ() + 1);
                // Move Z down by one to include the north line as players are likely to use it for registration
                lowerMapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ - 1);
                MapAreaCache.reset(lowerMapCorner, true);
                state = State.SelectingUpperMapArea;
                info("§aLowerMap Area selected. Select the §bUpper Map Area.");
                return;
            case SelectingUpperMapArea:
                hitPos = packet.getHitResult().getBlockPos().relative(packet.getHitResult().getDirection());
                adjustedX = Utils.getIntervalStart(hitPos.getX());
                adjustedZ = Utils.getIntervalStart(hitPos.getZ() + 1);
                // Move Z down by one to include the north line as players are likely to use it for registration
                upperMapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ - 1);
                if (upperMapCorner.getX() == lowerMapCorner.getX() &&
                    upperMapCorner.getZ() == lowerMapCorner.getZ() &&
                    upperMapCorner.getY() > lowerMapCorner.getY()) {
                    state = State.SelectingDumpStation;
                    info("§aUpper Map Area selected. Throw an item into each §bDump Station. " +
                        "§aInteract with the §bCartography Table §aafter you selected all.");
                }
                return;
            case SelectingDumpStation:
                BlockPos blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Tuple<>(blockPos, mc.player.position());
                    info("§aCartography Table selected. Select the §bFinished Map Chest.");
                    state = State.SelectingFinishedMapChest;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Tuple<>(blockPos, mc.player.position());
                    info("§aFinished Map Chest selected. Select all §bUsed Tool Chest.");
                    state = State.SelectingUsedToolChest;
                }
                break;
            case SelectingUsedToolChest:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    usedToolChest = new Tuple<>(blockPos, mc.player.position());
                    info("§aUsed Pickaxe Chest selected. Select all §bMaterial-, Tool-, and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlocks.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlocks.get().contains(blockState.getBlock())) startBuilding();
                break;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == null) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) Utils.setForwardPressed(false);
        }

        if (!(event.packet instanceof ClientboundContainerSetContentPacket packet)) return;

        if (state.equals(State.AwaitRegisterResponse)) {
            Item foundItem = null;
            ItemStack foundItemStack = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.items().size() - 36; i++) {
                ItemStack stack = packet.items().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    foundItemStack = stack;
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("§aRegistered §bMap Chest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.position());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (ToolUtils.isTool(foundItemStack)) {
                toolSet.add(foundItemStack);
            }
            info("Registered item: §a" + foundItem.getName(foundItem.getDefaultInstance()).getString());
            if (!materialDict.containsKey(foundItem)) materialDict.put(foundItem, new ArrayList<>());
            ArrayList<Tuple<BlockPos, Vec3>> oldList = materialDict.get(foundItem);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.position());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
            return;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse,
            State.AwaitUsedToolChestResponse, State.AwaitUsedToolChestResponseDumpAll);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
        }
    }

    private void handleInventoryPacket(ClientboundContainerSetContentPacket packet) {
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                boolean foundMaterials = false;
                List<Integer> slots = IntStream.rangeClosed(0, packet.items().size() - 37)
                    .boxed()
                    .collect(Collectors.toList());
                Collections.shuffle(slots);
                for (int slot : slots) {
                    ItemStack stack = packet.items().get(slot);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && (stack.getCount() == 64 || !stack.isStackable())) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
                            checkpoints.add(1, new Tuple(getBestDumpStation().getA(), new Tuple("calculateRefill", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(slot);
                        Triple<Item, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                //Search for map and glass pane
                for (int slot = 0; slot < packet.items().size() - 36; slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if (mapSlot == -1 || paneSlot == -1) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet);
                Utils.getOneItem(paneSlot, true, availableSlots, availableHotBarSlots, packet);
                state = State.Walking;
                break;
            case AwaitCartographyResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                boolean searchingMap = true;
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.items().get(slot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.items().get(slot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                mc.gameMode.handleContainerInput(packet.containerId(), 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                checkpoints.add(new Tuple(finishedMapChest.getB(), new Tuple("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }

                // Master joins mining process if a certain amount of lower lines are left to mine
                if (lowerMiningCommand.size() >= masterHelpMinLines.get()) {
                    setInterval(new Tuple<>(127, 127));
                    mineLine(-1);
                    return;
                }
                // Else wait for all slaves to finish
                state = State.AwaitNBTFile;
                break;
            case AwaitUsedToolChestResponse:
                interactTimeout = 0;
                // ToDo: Add timeout here?
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack itemStack = packet.items().get(slot);
                    if (ToolUtils.isTool(itemStack) && ToolUtils.getRemainingDamage(itemStack) <= minDurability.get()) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                    }
                }
                state = State.Walking;
                break;
            case AwaitUsedToolChestResponseDumpAll:
                interactTimeout = 0;
                // ToDo: Add timeout here?
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack itemStack = packet.items().get(slot);
                    if (ToolUtils.isTool(itemStack)) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                    }
                }
                // Dump blocks too
                checkpoints.add(0, new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
                state = State.Walking;
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / (long) placeDelay.get());
        lastTickTime += allowedPlacements * placeDelay.get();

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: §a" + state);
        }

        // Delegate upper/lower mining commands to available slaves
        for (int i = SlaveSystem.slaves.size() - 1; i >= Math.max(0, SlaveSystem.slaves.size()-2); i--) {
            ArrayList<Integer> miningCommand = i % 2 == 1 ? lowerMiningCommand : upperMiningCommand;
            if (!miningCommand.isEmpty()) {
                String slave = SlaveSystem.slaves.get(i);
                if (SlaveSystem.finishedSlavesDict.get(slave)) {
                    int targetLine = miningCommand.removeFirst();
                    SlaveSystem.sendToSlave(slave, "mine:" + targetLine);
                    SlaveSystem.finishedSlavesDict.put(slave, false);
                    SlaveSystem.activeSlavesDict.put(slave, true);
                }
            }
        }

        // Delegate last lower mining commands to master if in master mining mode
        if (masterMiningReady && !lowerMiningCommand.isEmpty()) {
            masterMiningReady = false;
            mineLine(lowerMiningCommand.removeLast());
        }

        if (state.equals(State.AwaitMinedSuppression)) {
            int startZ = Math.max(0, awaitSuppressionZ-15);
            int endZ = Math.min(128, awaitSuppressionZ+15);
            if (SlaveSystem.mapCompletionState.isLineComplete(true, suppressedLines-1, startZ, endZ, false)) {
                state = State.Walking;
            } else {
                return;
            }
        }

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getA());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        if (timeoutTicks > 0) {
            if (mc.player.onGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
            return;
        }

        // Swap into Hotbar
        if (toBeSwappedSlot != -1) {
            swapIntoHotbar(toBeSwappedSlot);
            toBeSwappedSlot = -1;
            if (postSwapDelay.get() != 0) {
                timeoutTicks = postSwapDelay.get();
                return;
            }
        }

        // Restocking
        if (restockBacklogSlots.size() > 0) {
            int slot = restockBacklogSlots.remove(0);
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 1, ContainerInput.QUICK_MOVE, mc.player);
            if (restockBacklogSlots.isEmpty()) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        // Jobs while mining
        if (state.equals(State.Mining) && !checkpoints.isEmpty()
            && checkpoints.getFirst().getB().getA() == "mineEnd") {

            // Intermediate reporting of upper slave for parallel suppression
            if (SlaveSystem.isSlave && workOnUpper) {
                BlockPos nextPos = getNextMiningPos();
                boolean northToSouth = checkpoints.getFirst().getA().z() > mc.player.getZ();
                int minedZ;
                if (nextPos == null) {
                    if (northToSouth) minedZ = 128;
                    else minedZ = 0;
                } else {
                    minedZ = nextPos.subtract(getActiveMapCorner()).getZ();
                    int northToSouthModifier = northToSouth ? -1 : 1;
                    minedZ += northToSouthModifier;
                }

                if (minedZ > -1 && minedZ < 129 && Math.abs(minedZ - lastReportedZ) > 0) {
                    lastReportedZ = minedZ;
                    if (northToSouth) {
                        SlaveSystem.sendMessageToMaster("placeStatus:" + workingInterval.getA() + ":"
                            + workingInterval.getB() + ":" + 0 + ":" + minedZ + ":" + false + ":" + true);
                    } else {
                        SlaveSystem.sendMessageToMaster("placeStatus:" + workingInterval.getA() + ":"
                            + workingInterval.getB() + ":" + minedZ + ":" + 128 + ":" + false + ":" + true);
                    }
                }
            }

            // Mined Block Collection
            if (collectBlocks.get()) {
                int zModifier = checkpoints.getFirst().getA().z() > mc.player.getZ() ? -1 : 1;
                // Master mines right-to-left, slaves left-to-right
                int xModifier = SlaveSystem.isSlave ? 0 : 2;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if ((zModifier == -1 && !collectAllDirections.get()) || isInventoryFull()) break;
                    if (entity instanceof ItemEntity itemEntity
                        && itemEntity.verticalCollisionBelow
                        && !itemEntity.hasPickUpDelay()
                        && itemEntity.getOwner() == null
                        && itemEntity.getY() >= getActiveMapCorner().getY()
                        && itemEntity.getY() < getActiveMapCorner().getY() + 2
                        && itemEntity.getX() < lowerMapCorner.getX() + workingInterval.getB() + xModifier + 1.6f
                        && itemEntity.getX() > lowerMapCorner.getX() + workingInterval.getA() + xModifier - 2.6f
                        && itemEntity.getZ() <= lowerMapCorner.getZ() + 129
                        && itemEntity.getZ() >= lowerMapCorner.getZ() - 2
                        && itemEntity.getZ() * zModifier > (mc.player.getZ() + minZCollectionDistance.get()) * zModifier) {
                        // Cap the collectionPos to avoid the player trying to walk into walls
                        double clampedX;
                        if (SlaveSystem.isSlave) {
                            clampedX = Math.clamp(itemEntity.getX(), lowerMapCorner.getX() + 0.4f, lowerMapCorner.getX() + workingInterval.getB() + 0.6f);
                        } else {
                            clampedX = Math.clamp(itemEntity.getX(), lowerMapCorner.getX() + workingInterval.getA() + 0.4f, lowerMapCorner.getX() + 127.6f);
                        }
                        Vec3 collectionPos = new Vec3(
                            clampedX,
                            itemEntity.getY(),
                            Math.min(lowerMapCorner.getZ() + lowerMapLayer[0].length - 0.4f, itemEntity.getZ()));
                        checkpoints.addFirst(new Tuple<>(collectionPos, new Tuple<>("sprint", null)));
                    }
                }
            }
        }

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && nextBlockPos != null) {
            // Break block if miningPos is not null. Stop walking if further away than maxMiningRange
            if (MapAreaCache.getCachedBlockState(nextBlockPos).isAir()) {
                nextBlockPos = null;
                state = State.Mining;
            } else {
                BlockPos mapCorner = getActiveMapCorner();
                Vec3 lookPos = new Vec3(mc.player.getX(), Vec3.atCenterOf(mapCorner).y + 0.5f, Vec3.atCenterOf(nextBlockPos).z);
                mc.player.setXRot((float) Rotations.getPitch(lookPos));
                BlockUtils.breakBlock(nextBlockPos, true);

                if (Math.abs(Vec3.atCenterOf(nextBlockPos).z() - mc.player.getZ()) <= minMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }

                if (state.equals(State.AwaitBlockBreak)) {
                    Utils.setForwardPressed(false);
                    return;
                }
            }
        }

        if (state.equals(State.AwaitPlacedSuppression)) {
            if (SlaveSystem.mapCompletionState.isLineComplete(true, suppressedLines+1, true) &&
                SlaveSystem.mapCompletionState.isLineComplete(false, suppressedLines+1, true)) {

                suppressedLines++;
                if (suppressedLines > 0) {
                    // Suppress one line
                    suppressPath();
                    // Wait for timeout if it was the last line
                    if (suppressedLines == -1) return;
                } else {
                    // Only continue filling map on first iteration
                    loadFirstLinePath();
                }
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping || state == State.DumpingBlocks) {
            int dumpSlot = getDumpSlot(state == State.DumpingBlocks);
            if (dumpSlot == -1) {
                state = State.Walking;
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getItem(dumpSlot).getHoverName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
            if (!SlaveSystem.isSlave) {
                if (!SlaveSystem.allSlavesFinished()) return;
                if (mapFile != null) {
                    try {
                        if (moveToFinishedFolder.get()) {
                            File targetFile = new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName());
                            Files.move(
                                mapFile.toPath(),
                                targetFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            );
                        }
                    } catch (Exception e) {
                        warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
                        e.printStackTrace();
                    }
                }
            }

            if (!prepareNextMapFile()) {
                return;
            }
            startBuilding();
        }

        // Handle Block Entity interaction response
        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            if (mc.gui.screen() != null) {
                mc.player.closeContainer();
            }
            closeNextInvPacket = false;
        }

        if (!(state.equals(State.Walking) || state.equals(State.Mining))) return;

        // Main Loop for Building & Mining
        Utils.setForwardPressed(true);
        Vec3 goal = checkpoints.get(0).getA();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Tuple<String, BlockPos> checkpointAction = checkpoints.get(0).getB();
            if (debugPrints.get() && checkpointAction.getA() != null)
                info("Reached: §a" + checkpointAction.getA());
            if (snapToCheckpoints.get()) mc.player.setPos(goal.x, mc.player.getY(), goal.z);
            checkpoints.remove(0);

            switch (checkpointAction.getA()) {
                case "lineEnd":
                    boolean reachedNorthSide = goal.z < lowerMapCorner.getZ() + lowerMapLayer.length/2;
                    // ToDo: Check last line was completed without missing blocks
                    boolean placing = state.equals(State.Walking) ? true : false;
                    int newPlacedLine = Math.min(workingInterval.getB()+1, lastPlacedLine + linesPerRun.get());
                    setLineFinished(lastPlacedLine, newPlacedLine-1, placing, workOnUpper);
                    lastPlacedLine = newPlacedLine;
                    // Remove finished line from workingInterval. Flip snake pattern
                    setInterval(new Tuple<>(workingInterval.getA() + linesPerRun.get(), workingInterval.getB()));
                    buildPath(reachedNorthSide, false);
                    break;
                case "mapMaterialChest":
                    state = State.AwaitMapChestResponse;
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getA();
                    interactWithBlock(mapMaterialChest);
                    return;
                case "selectMapSlot":
                    // Select map slot
                    FindItemResult mapItem = InvUtils.findInHotbar(Items.MAP);
                    if (!mapItem.found() || !availableHotBarSlots.contains(mapItem.slot())) {
                        warning("Failed to find map item in hotbar! Stopping...");
                        checkpoints.add(0, new Tuple<>(mc.player.position(), new Tuple<>("selectMapSlot", null)));
                        toggle();
                        return;
                    }
                    mc.player.getInventory().setSelectedSlot(mapItem.slot());
                    break;
                case "fillMap":
                    state = State.AwaitPlacedSuppression;
                    Utils.setForwardPressed(false);
                    // Load map and wait for it to update
                    mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYRot(), mc.player.getXRot()));
                    timeoutTicks = mapLoadTimeout.get();
                    return;
                case "awaitMapUpdate":
                    Utils.setForwardPressed(false);
                    timeoutTicks = mapLoadTimeout.get();
                    return;
                case "awaitSuppression":
                    Utils.setForwardPressed(false);
                    state = State.AwaitPlacedSuppression;
                    if (suppressedLines < 0) return;
                    if (suppressedLines > 0) {
                        if(debugPrints.get()) info("Add lower mining command: " + (suppressedLines-1));
                        lowerMiningCommand.add(suppressedLines-1);
                    }
                    if (suppressedLines < lowerMapLayer.length / 2) {
                        if(debugPrints.get()) info("Add upper mining command: " + (suppressedLines));
                        upperMiningCommand.add(suppressedLines);
                    }
                    return;
                case "startSneak":
                    Utils.setSneakPressed(true);
                    break;
                case "stopSneak":
                    Utils.setForwardPressed(false);
                    Utils.setSneakPressed(false);
                    timeoutTicks = mapLoadTimeout.get();
                    awaitSuppressionZ =  (int) Math.floor(goal.z) - lowerMapCorner.getZ();
                    int startZ = Math.max(0, awaitSuppressionZ-15);
                    int endZ = Math.min(128, awaitSuppressionZ+15);
                    if (!SlaveSystem.mapCompletionState.isLineComplete(true, suppressedLines-1, startZ, endZ, false)) {
                        state = State.AwaitMinedSuppression;
                    }
                    return;
                case "swapMap":
                    // Avoid destroying the map by loading it
                    mapItem = InvUtils.findInHotbar(Items.FILLED_MAP);
                    if (!mapItem.found()) break;
                    // If there is a non-hotbar-slot. Swap the map to it
                    if (availableSlots.size() > availableHotBarSlots.size()) {
                        for (int slot : availableSlots) {
                            if (availableHotBarSlots.contains(slot)) continue;
                            Utils.performSwap(slot, mapItem.slot());
                        }
                    } else {
                        // Switch to something that is not the filled map
                        int targetSlot = mapItem.found() ? (mapItem.slot()+1) % 9 : 8;
                        mc.player.getInventory().setSelectedSlot(targetSlot);
                    }
                    break;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    Utils.setForwardPressed(false);
                    interactWithBlock(cartographyTable.getA());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getA());
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setForwardPressed(false);
                    Tuple<Float, Float> throwingAngle = getBestDumpStation().getB();
                    mc.player.setYRot(throwingAngle.getA());
                    mc.player.setXRot(throwingAngle.getB());
                    return;
                case "dumpBlocks":
                    state = State.DumpingBlocks;
                    Utils.setForwardPressed(false);
                    throwingAngle = getBestDumpStation().getB();
                    mc.player.setYRot(throwingAngle.getA());
                    mc.player.setXRot(throwingAngle.getB());
                    return;
                case "calculateMiningRefill":
                    refillMiningInventory(true);
                    return;
                case "calculateRefill":
                    HashMap<Item, Integer> requiredItems = getRequiredItems();
                    Tuple<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(invInformation.getB());
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    Utils.setForwardPressed(false);
                    interactWithBlock(checkpointAction.getB());
                    return;
                case "mineBegin":
                    lastReportedZ = -1;
                    break;
                case "mineEnd":
                    if (goal.z < lowerMapCorner.getZ() + lowerMapLayer.length/2
                        && ((SlaveSystem.isSlave && workingInterval.getB() != lowerMapLayer.length-1)
                        || (!SlaveSystem.isSlave && workingInterval.getA() != 0))) {
                        runsUntilDump--;
                        if (runsUntilDump <= 0) {
                            runsUntilDump = runsBetweenDumps.get();
                            int xOffset = SlaveSystem.isSlave ? 2 : -2;
                            checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("dumpBlocks", null)));
                            checkpoints.add(new Tuple(mc.player.position().add(xOffset,0,0), new Tuple("mineFinished", null)));
                            return;
                        }
                    }
                    finishMineLine();
                    return;
                case "mineFinished":
                    finishMineLine();
                    return;
                case "slaveFinished":
                    setMiningFinished();
                    return;
                case "usedToolChest":
                    state = State.AwaitUsedToolChestResponse;
                    Utils.setForwardPressed(false);
                    interactWithBlock(usedToolChest.getA());
                    return;
                case "usedToolChestDumpAll":
                    state = State.AwaitUsedToolChestResponseDumpAll;
                    Utils.setForwardPressed(false);
                    interactWithBlock(usedToolChest.getA());
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (state.equals(State.Walking)) {
                    // Done Building
                    info("Done placing map");
                    checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("dumpBlocks", null)));
                    if (SlaveSystem.isSlave) {
                        checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("slaveFinished", null)));
                    } else {
                        // Master
                        fillMapPath();
                    }
                }
            }
            goal = checkpoints.get(0).getA();
        }

        // Set yaw rotation to goal
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ() + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        mc.player.setYRot((float) Rotations.getYaw(new Vec3(goal.x, goal.y, lookZ)));

        String nextAction = checkpoints.get(0).getB().getA();

        // Set sprint mode
        final List<String> noSprintActions = Arrays.asList("lineBegin", "lineEnd", "mineBegin", "mineEnd");
        if (noSprintActions.contains(nextAction) && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("lineBegin", "lineEnd", "sprint", "mineBegin", "mineEnd");
        if (!allowPlaceActions.contains(nextAction)) return;

        BlockPos tempPos;
        if (state.equals(State.Walking)) {
            tempPos = getNextPlacementPos(goal.z >= mc.player.getZ());
        } else {
            tempPos = getNextMiningPos();
        }

        // ToDo fix this
        if (tempPos == null || PlayerUtils.distanceTo(Vec3.atCenterOf(tempPos)) > interactionRange.get()) return;
        nextBlockPos = tempPos;

        if (state.equals(State.Walking)) {
            tryPlacingBlock();
        } else {
            BlockPos mapCorner = getActiveMapCorner();
            Vec3 lookPos = new Vec3(mc.player.getX(), Vec3.atCenterOf(mapCorner).y + 0.5f, Vec3.atCenterOf(nextBlockPos).z);
            mc.player.setXRot((float) Rotations.getPitch(lookPos));
            BlockState blockState = MapAreaCache.getCachedBlockState(nextBlockPos);
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockState);
            int bestHotbarSlot = -1;
            // Search for the tool with the optimal tool type and minimal durability above the threshold
            for (int slot : availableHotBarSlots) {
                if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
                ItemStack itemStack = mc.player.getInventory().getItem(slot);
                if (itemStack.getItem().equals(bestTool.getItem())) {
                    int remainingDamage = ToolUtils.getRemainingDamage(itemStack);
                    if (remainingDamage < minDurability.get()) continue;
                    if (bestHotbarSlot == -1 || remainingDamage < ToolUtils.getRemainingDamage(bestTool)) {
                        bestHotbarSlot = slot;
                        bestTool = itemStack;
                    }
                }
            }
            if (bestHotbarSlot == -1) {
                warning("No tool with enough durability found to mine blocks");
                return;
            }
            InvUtils.swap(bestHotbarSlot, false);
            BlockUtils.breakBlock(nextBlockPos, true);
            state = State.Mining;
            if (Math.abs(Vec3.atCenterOf(nextBlockPos).z() - mc.player.getZ()) <= minMiningRange.get()) {
                state = State.AwaitBlockBreak;
            }
        }
    }

    // Restocking

    private Tuple<BlockPos, Vec3> getBestChest(Item item) {
        ArrayList<Tuple<BlockPos, Vec3>> list = new ArrayList<>();
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + item.getName(item.getDefaultInstance()).getString());
            toggle();
            return new Tuple<>(new BlockPos(0, 0, 0), new Vec3(0, 0, 0));
        }
        ArrayList<Tuple<BlockPos, Vec3>> bestList = new ArrayList<>();
        // Get lowest/heighest chest (depending on layer) as 1. criteria
        for (Tuple<BlockPos, Vec3> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getA())) continue;
            if (bestList.isEmpty() || ((bestList.getFirst().getA().getY() <= p.getA().getY()) && workOnUpper)
                || ((bestList.getFirst().getA().getY() >= p.getA().getY()) && !workOnUpper)) {
                // Remove all previous results if new one is truly better
                if (!bestList.isEmpty() && bestList.getFirst().getA().getY() != p.getA().getY()) {
                    bestList.clear();
                }
                bestList.add(p);
            }
        }
        if (bestList.isEmpty()) {
            checkedChests.clear();
            return getBestChest(item);
        }
        // Get nearest chest as 2. criteria
        Vec3 bestPos = null;
        BlockPos bestChestPos = null;
        for (Tuple<BlockPos, Vec3> p : bestList) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getA())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getB()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getB();
                bestChestPos = p.getA();
            }
        }
        return new Tuple(bestChestPos, bestPos);
    }

    private Tuple<Vec3, Tuple<Float, Float>> getBestDumpStation() {
        return getBestDumpStation(mc.player.position());
    }

    private Tuple<Vec3, Tuple<Float, Float>> getBestDumpStation(Vec3 pos) {
        Vec3 bestPos = null;
        Tuple<Float, Float> throwAngle = null;
        for (int i = 0; i < dumpStations.size(); i++) {
            if (bestPos == null || pos.distanceTo(dumpStations.get(i).getA()) < pos.distanceTo(bestPos)) {
                bestPos = dumpStations.get(i).getA();
                throwAngle = dumpStations.get(i).getB();
            }
        }
        return new Tuple(bestPos, throwAngle);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required build materials
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        for (Item item : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(item);
            requiredItems.put(item, oldAmount - invMaterial.get(item));
        }

        for (Item item : requiredItems.keySet()) {
            if (requiredItems.get(item) <= 0) continue;
            int stacks = (int) Math.ceil((float) requiredItems.get(item) / 64f);
            if(debugPrints.get()) info("Restocking §a" + stacks + " stacks " + item.getName(item.getDefaultInstance()).getString() + " (" + requiredItems.get(item) + ")");
            restockList.add(0, Triple.of(item, stacks, requiredItems.get(item)));
        }
        addClosestRestockCheckpoint();
    }

    private void refillMiningInventory(boolean includeFutureBlocks) {
        // Fills restockList mining tools for mining current/future blocks
        restockList.clear();
        // Calculate total uses per tool
        HashMap<ItemStack, Integer> toolUseDict = new HashMap<>();
        forEachMapBlock((x, z, y, blockState, block) -> {
            BlockState cachedBlockState = MapAreaCache.getCachedBlockState(getActiveMapCorner().offset(x,y,z));
            Block cachedBlock = cachedBlockState == null ? null : cachedBlockState.getBlock();
            Block miningBlock = includeFutureBlocks ? getActiveMapLayer()[x][z][y] : cachedBlock;
            if (miningBlock == null) return true;
            BlockState miningBlockState = miningBlock.defaultBlockState();
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, miningBlockState);
            if (bestTool == null || (miningBlockState.getDestroySpeed(mc.level, getActiveMapCorner().offset(x,y,z)) == 0.0f)) return true;
            if (toolUseDict.containsKey(bestTool)) {
                toolUseDict.put(bestTool, toolUseDict.get(bestTool) + 1);
            } else {
                toolUseDict.put(bestTool, 1);
            }
            return true;
        }, true);

        for (ItemStack itemStack : toolUseDict.keySet()) {
            // Fetch unbreaking enchantment level
            Holder<Enchantment> unbreaking = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING);
            int unbreakingLevel = itemStack.getEnchantments().getLevel(unbreaking);

            int rawUses = toolUseDict.get(itemStack);
            double adjustedUses = (float) rawUses / (float) (unbreakingLevel + 1) * (1 + durabilityBuffer.get());
            for (int slot : availableSlots) {
                ItemStack invTool = mc.player.getInventory().getItem(slot);
                if (invTool.getItem() == itemStack.getItem()) {
                    adjustedUses = Math.max(0, adjustedUses - (ToolUtils.getRemainingDamage(invTool) - minDurability.get()));
                }
            }

            int itemsNeeded = (int) Math.ceil((float) adjustedUses / (float) itemStack.getMaxDamage());
            if (itemsNeeded == 0) continue;
            if (debugPrints.get()) info("Restocking §a" + itemsNeeded + " " + itemStack.getItem().getName(itemStack.getItem().getDefaultInstance()).getString() + " (" + rawUses + " uses)");
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), itemsNeeded, itemsNeeded));
        }

        float goalX = 0.5f;
        if (!SlaveSystem.isSlave) goalX = 126.5f;
        checkpoints.add(new Tuple<>(Vec3.atCenterOf(getActiveMapCorner()).add(goalX,-0.5f, -1), new Tuple<>("slaveFinished", null)));

        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Tuple<BlockPos, Vec3> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Tuple<BlockPos, Vec3> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos.getA() == null) {
                warning("No chest found for " + entry.getLeft().getName(entry.getLeft().getDefaultInstance()).getString());
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getB());
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        //Set closest material as first and as checkpoint
        restockList.remove(closestEntry);
        restockList.addFirst(closestEntry);
        checkpoints.addFirst(new Tuple(restockPos.getB(), new Tuple("refill", restockPos.getA())));
    }

    private void endRestocking() {
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedChest);
            Tuple<BlockPos, Vec3> bestRestockPos = getBestChest(getMaterialFromPos(lastInteractedChest));
            checkpoints.addFirst(new Tuple<>(bestRestockPos.getB(), new Tuple<>("refill", bestRestockPos.getA())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    private Item getMaterialFromPos(BlockPos pos) {
        for (Item item : materialDict.keySet()) {
            for (Tuple<BlockPos, Vec3> p : materialDict.get(item)) {
                if (p.getA().equals(pos)) return item;
            }
        }
        warning("Could not find material for chest position : " + pos.toShortString());
        toggle();
        return null;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos chestPos) {
        Utils.setForwardPressed(false);
        mc.player.setYRot((float) Rotations.getYaw(Vec3.atCenterOf(chestPos)));
        mc.player.setXRot((float) Rotations.getPitch(Vec3.atCenterOf(chestPos)));

        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(chestPos), Utils.getInteractionSide(chestPos), chestPos, false);
        BlockUtils.interact(hitResult, InteractionHand.MAIN_HAND, true);

        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private void tryPlacingBlock() {
        BlockPos relativePos = nextBlockPos.subtract(getActiveMapCorner());
        Item material = getActiveMapLayer()[relativePos.getX()][relativePos.getZ()][relativePos.getY()].asItem();
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(nextBlockPos, InteractionHand.MAIN_HAND, slot, rotatePlace.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                toBeSwappedSlot = slot;
                Utils.setForwardPressed(false);
                timeoutTicks = preSwapDelay.get();
                return;
            }
        }
        if (lastSwappedMaterial == material) return;      //Wait for swapped material
        if (debugPrints.get()) info("No " + material.getName(material.getDefaultInstance()).getString() + " found in inventory. Resetting...");
        Vec3 pathCheckpoint = new Vec3(mc.player.getX(), lowerMapCorner.getY(), Vec3.atCenterOf(lowerMapCorner.north()).z());

        if (!checkpoints.isEmpty() && checkpoints.getFirst().getA().z < mc.player.getZ()
            && mc.player.getZ() < lowerMapCorner.getZ() + lowerMapLayer.length/2) {
            // If player walks north and has built more than 50% continue placing south-to-north
            checkpoints.addFirst(new Tuple(mc.player.position(), new Tuple("walkRestock", null)));
            checkpoints.addFirst(new Tuple(pathCheckpoint, new Tuple("walkRestock", null)));
        } else {
            // Else just start the line again north-to-south
            checkpoints.clear();
            buildPath(true, true);
        }
        checkpoints.addFirst(new Tuple(getBestDumpStation().getA(), new Tuple("calculateRefill", null)));
        checkpoints.addFirst(new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
        checkpoints.addFirst(new Tuple(pathCheckpoint, new Tuple("walkRestock", null)));
    }

    private BlockPos getNextPlacementPos(boolean movingSouth) {
        // Get next block in working interval to place
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += linesPerRun.get()) {
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                    for (int y = 0; y < lowerMapLayer[0][0].length; y++) {
                        int adjustedZ = !movingSouth ? lowerMapLayer[0].length-1-z : z;
                        BlockPos blockPos = getActiveMapCorner().offset(adjustedX, y, adjustedZ);
                        BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                        // Check if block is not placed already, should not be placed at all, or is too far east
                        // On the last line, ignore the x check as the player is walking on the line
                        if (blockState.isAir() && getActiveMapLayer()[adjustedX][adjustedZ][y] != null
                            && (mc.player.getX() > Vec3.atCenterOf(blockPos).x() + 0.5f
                            || (mc.player.getBlockX() - getActiveMapCorner().getX() >= workingInterval.getB()
                            && !movingSouth &&  mc.player.getZ() + 0.3f < Vec3.atCenterOf(blockPos).z()))) {
                            return blockPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos getNextMiningPos() {
        // Get next block in working interval to mine
        boolean flippedX = !SlaveSystem.isSlave;
        boolean flippedZ = false;
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += 2) {
            int reversedX = flippedX ? lowerMapLayer.length - 2 - x + workingInterval.getA() : x;
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < 2; lineBonus++) {
                    int adjustedX = reversedX + lineBonus;
                    if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                    for (int y = lowerMapLayer[0][0].length-1; y >= 0; y--) {
                        int adjustedZ = flippedZ ? lowerMapLayer[0].length-1-z : z;
                        BlockPos blockPos = getActiveMapCorner().offset(adjustedX, y, adjustedZ);
                        BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                        if (!blockState.isAir()) {
                            return blockPos;
                        }
                    }
                }
            }
            flippedZ = !flippedZ;
        }
        return null;
    }

    // Path and Building Management

    private void buildPath(boolean startNorthSide, boolean sprintFirst) {
        // info("buildpath | northside: " + startNorthSide + " | sprint: " + sprintFirst);
        // Iterate over map and skip completed lines. Player has to be able to see the complete map area
        // Fills checkpoints list
        boolean northToSouth = startNorthSide;
        boolean isLastLine = false;
        checkpoints.clear();
        for (int x = workingInterval.getA(); x <= workingInterval.getB(); x += linesPerRun.get()) {
            // Last line hast to be south-to-north to not get stuck
            if (!Utils.isInInterval(workingInterval, x+linesPerRun.get()) && northToSouth) {
                northToSouth = false;
                isLastLine= true;
            }

            boolean lineFinished = true;
            int lastPlacedZ = 128;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                int adjustedX = x + lineBonus;
                if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                for (int z = 0; z < upperMapLayer[0].length; z++) {
                    int adjustedZ = northToSouth ? z : lowerMapLayer[0].length - z - 1;
                    for (int y = 0; y < upperMapLayer[0][0].length; y++) {
                        BlockState blockState = MapAreaCache.getCachedBlockState(getActiveMapCorner().offset(adjustedX, y, adjustedZ));
                        if (blockState.isAir() && getActiveMapLayer()[adjustedX][adjustedZ][y] != null) {
                            //If there is a replaceable block and not an ignored block type at the position. Mark the line as not done
                            lineFinished = false;
                        }
                        if (!blockState.isAir() && adjustedX == workingInterval.getB()) lastPlacedZ = adjustedZ-2;
                    }
                }
            }
            if (lineFinished) {
                setLineFinished(x, x + linesPerRun.get() - 1, true, workOnUpper);
                continue;
            }
            float cpX = Math.min(workingInterval.getB() , x + linesPerRun.get());
            float cp2Z = isLastLine ? lastPlacedZ + 0.2f : 128 + 0.2f;
            Vec3 cp1 = Vec3.atCenterOf(getActiveMapCorner()).add(cpX, -0.5f, -2);
            Vec3 cp2 = Vec3.atCenterOf(getActiveMapCorner()).add(cpX, -0.5f, cp2Z);

            if (northToSouth) {
                checkpoints.add(new Tuple(cp1, new Tuple("lineBegin", null)));
                checkpoints.add(new Tuple(cp2, new Tuple("lineEnd", null)));
            } else {
                if (isLastLine) checkpoints.add(new Tuple(cp1, new Tuple("sprint", null)));
                checkpoints.add(new Tuple(cp2, new Tuple("lineBegin", null)));
                checkpoints.add(new Tuple(cp1, new Tuple("lineEnd", null)));
            }
            northToSouth = !northToSouth;
        }

        if (checkpoints.isEmpty()) {
            // FallBack in case everything is built
            checkpoints.add(new Tuple(mc.player.position(), new Tuple("sprint", null)));
        } else if (sprintFirst) {
            // Set the first checkpoint to sprint
            Vec3 firstVec3 = checkpoints.removeFirst().getA();
            checkpoints.addFirst(new Tuple(firstVec3, new Tuple("sprint", null)));
        }
    }

    private void minePath(int lineX) {
        checkpoints.clear();
        Vec3 cp1 = Vec3.atCenterOf(getActiveMapCorner()).add(lineX-0.5f, -0.5f, -1);
        Vec3 cp2 = Vec3.atCenterOf(getActiveMapCorner()).add(lineX-0.5f, -0.5f, lowerMapLayer[0].length-1);
        if (mc.player.getZ() < lowerMapCorner.getZ() + lowerMapLayer[0].length / 2) {
            checkpoints.add(new Tuple(cp1, new Tuple("mineBegin", null)));
            checkpoints.add(new Tuple(cp2, new Tuple("mineEnd", null)));
        } else {
            checkpoints.add(new Tuple(cp2, new Tuple("mineBegin", null)));
            checkpoints.add(new Tuple(cp1, new Tuple("mineEnd", null)));
        }
    }

    private void fillMapPath() {
        state = State.Walking;
        setInterval(new Tuple<>(0, -1));

        if (Utils.hasTool(availableSlots)) {
            checkpoints.add(new Tuple(usedToolChest.getB(), new Tuple("usedToolChestDumpAll", null)));
        } else {
            checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
        }

        Tuple<BlockPos, Vec3> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(new Tuple(bestChest.getB(), new Tuple("mapMaterialChest", bestChest.getA())));
        checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(129,-1,-2), new Tuple("sprint", null)));
        checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(129,-3.5,1), new Tuple("selectMapSlot", null)));
        checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(125,-3.5,116), new Tuple("fillMap", null)));
    }

    private void loadFirstLinePath() {
        checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(125,-3.5,12), new Tuple("awaitMapUpdate", null)));
        checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(128,-3.5,15), new Tuple("awaitSuppression", null)));
        state = State.Walking;
    }

    private void suppressPath() {
        state = State.Walking;
        int xOffset = 125 + suppressedLines*2;
        List<Integer> suppressionCheckpoints = Arrays.asList(15, 41, 65, 89, 113);

        if (suppressedLines <= 64) {
            // When player is south of the center, walk checkpoints south-to-north, otherwise north-to-south
            if (mc.player.getZ() > lowerMapCorner.getZ() + lowerMapLayer[0].length/2) {
                suppressionCheckpoints = suppressionCheckpoints.reversed();
            }
            // Generate Suppression Path
            for (Integer suppressionCheckpoint : suppressionCheckpoints) {
                Vec3 entry = Vec3.atCenterOf(lowerMapCorner).add(xOffset + 1.4f,-3.5, suppressionCheckpoint);
                checkpoints.add(new Tuple(entry, new Tuple("startSneak", null)));
                checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(xOffset,-3.5, suppressionCheckpoint), new Tuple("stopSneak", null)));
                checkpoints.add(new Tuple(entry, new Tuple("sprint", null)));
            }
            // Go to next suppression line
            checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(xOffset + 2,-3.5, suppressionCheckpoints.getLast()), new Tuple("awaitSuppression", null)));
        } else {
            suppressedLines = -1;
            timeoutTicks = mapLoadTimeout.get();
            // Finished Suppression. Swap away & lock the finished map
            checkpoints.add(new Tuple(mc.player.position(), new Tuple("swapMap", null)));
            checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(129,-3.5,suppressionCheckpoints.getFirst()), new Tuple("sprint", null)));
            checkpoints.add(new Tuple(Vec3.atCenterOf(lowerMapCorner).add(129,-1,-2), new Tuple("sprint", null)));
            checkpoints.add(new Tuple(cartographyTable.getB(), new Tuple<>("cartographyTable", null)));
        }
    }

    private void initialSetup() {
        //Check if requirements to start building are met
        if (materialDict.isEmpty()) {
            warning("No Material Chests selected!");
            return;
        }
        if (toolSet.isEmpty()) {
            warning("No Tool Chests selected!");
            return;
        }
        if (mapMaterialChests.isEmpty()) {
            warning("No Map Chests selected!");
            return;
        }
        if (fillerBlock.get() == null) {
            warning("Filler-block setting must be set!");
            return;
        }
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(getActiveMapCorner(), true);
    }

    private void startBuilding() {
        initialSetup();
        info("§aStart building: §b" + mapFile.getName());

        if (!SlaveSystem.isSlave) {
            if (SlaveSystem.slaves.isEmpty()) {
                error("Need at least one slave to work on upper platform!");
                toggle();
                return;
            }

            SlaveSystem.setAllSlavesUnfinished();
            SlaveSystem.setAllSlavesActive();
            switchedToMiningSlaves.clear();

            if (SlaveSystem.slaves.size() > 4) {
                warning("Only 2-5 accounts are supported. More will not result in a speed up.");
                // Remove excess Slaves
                ArrayList<String> toBeRemoved = new ArrayList<>();
                for (int i = 5; i < SlaveSystem.slaves.size(); i++) {toBeRemoved.add(SlaveSystem.slaves.get(i));}
                for (String slave : toBeRemoved) {SlaveSystem.removeSlave(slave);}
            }

            // Slave 1: Placing upper layer
            SlaveSystem.sendToSlave(SlaveSystem.slaves.get(0), "interval:" + 0 + ":" + (lowerMapLayer.length-1));
            SlaveSystem.sendToSlave(SlaveSystem.slaves.get(0), "start");

            // Slave 3-4: Optional mining in parallel to placing (3. upper, 4. lower)
            for (int i = 3; i <= 4 ; i++) {
                if (SlaveSystem.slaves.size() >= i) {
                    // Prepare mining slaves
                    String slave = SlaveSystem.slaves.get(i-1);
                    SlaveSystem.sendToSlave(slave, "interval:" + -1 + ":" + -1);
                    SlaveSystem.sendToSlave(slave, "mine:"+ -1);
                }
            }

            // Slave 2 + Master: Placing lower layer
            if (SlaveSystem.slaves.size() >= 2) {
                // Master will only mine left to right since other lower slave will mine normally
                // 2-3 slaves -> Master will place half the map
                // 4+ slaves -> Master does not place anything
                int masterIntervalSize = SlaveSystem.slaves.size() >= 4 ? 0 : lowerMapLayer.length/2;
                String slave = SlaveSystem.slaves.get(1);
                SlaveSystem.sendToSlave(slave, "interval:" + masterIntervalSize + ":" + (lowerMapLayer.length-1));
                SlaveSystem.sendToSlave(slave, "start");
                if (masterIntervalSize == 0) {
                    fillMapPath();
                    return;
                } else {
                    setInterval(new Tuple<>(0, masterIntervalSize - 1));
                }
            } else {
                setInterval(new Tuple<>(0, (lowerMapLayer.length-1)));
            }
        }
        buildPath(true, true);

        state = State.Walking;
        checkpoints.add(0, new Tuple(getBestDumpStation().getA(), new Tuple("calculateRefill", null)));
        if (Utils.hasTool(availableSlots)) {
            checkpoints.add(0, new Tuple(usedToolChest.getB(), new Tuple("usedToolChestDumpAll", null)));
        } else {
            checkpoints.add(0, new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
        }
    }

    private void finishMineLine() {
        SlaveSystem.sendMessageToMaster("placeStatus:" + workingInterval.getA() + ":"
            + workingInterval.getB() + ":" + false + ":" + workOnUpper);
        setMiningFinished();

        if (!collectBlocks.get() || isInventoryFull()) return;

        AtomicBoolean isEmpty = new AtomicBoolean(true);
        forEachMapBlock((x, z, y, blockState, block) -> {
            if (blockState != null && !blockState.isAir()) {
                isEmpty.set(false);
                return false;
            }
            return true;
        }, true);

        if (isEmpty.get()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ItemEntity itemEntity
                    && itemEntity.getOwner() == null
                    && itemEntity.getY() >= getActiveMapCorner().getY()
                    && itemEntity.getY() <= getActiveMapCorner().getY() + 2
                    && itemEntity.getX() <= lowerMapCorner.getX() + 128
                    && itemEntity.getX() >= lowerMapCorner.getX()
                    && itemEntity.getZ() <= lowerMapCorner.getZ() + 129
                    && itemEntity.getZ() >= lowerMapCorner.getZ() - 2) {
                    // Cap the collectionPos to avoid the player trying to walk into walls
                    Vec3 collectionPos = new Vec3(
                        Math.clamp(itemEntity.getX(), lowerMapCorner.getX() + 0.4f, lowerMapCorner.getX() + 127.6f),
                        getActiveMapCorner().getY(),
                        Math.min(itemEntity.getZ(), lowerMapCorner.getZ() + 128.6f));
                    checkpoints.addFirst(new Tuple<>(collectionPos, new Tuple<>("sprint", null)));
                }
            }
        }
    }

    private void setLineFinished(int xStart, int xEnd, boolean placed, boolean isUpper) {
        xEnd = Math.min(workingInterval.getB(), xEnd);
        SlaveSystem.mapCompletionState.setInterval(xStart, xEnd, placed, isUpper);
        SlaveSystem.sendMessageToMaster("placeStatus:" + xStart + ":" +xEnd + ":" + placed + ":" + isUpper);
    }

    private void setMiningFinished() {
        Utils.setForwardPressed(false);
        if (!SlaveSystem.isSlave) {
            // Master Mining Mode
            if (lowerMiningCommand.isEmpty()) {
                state = State.AwaitNBTFile;
                return;
            }
            masterMiningReady = true;
            return;
        }
        state = State.StandBy;
        SlaveSystem.sendMessageToMaster("finished");
    }

    private BlockPos getActiveMapCorner() {
        return workOnUpper ? upperMapCorner : lowerMapCorner;
    }

    private Block[][][] getActiveMapLayer() {
        return workOnUpper ? upperMapLayer : lowerMapLayer;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        if (debugPrints.get()) info("Inventory slots available for building: " + availableSlots);
        if (availableHotBarSlots.isEmpty()) {
            warning("No free slots found in hot-bar!");
            availableSlots.clear();
            toggle();
            return false;
        }
        if (availableSlots.size() < 2) {
            warning("You need at least 2 free inventory slots!");
            availableSlots.clear();
            toggle();
            return false;
        }
        return true;
    }

    private int getDumpSlot(boolean dumpBlocks) {
        if (dumpBlocks) {
            // Dump anything that is not a tool
            for (int slot : availableSlots) {
                if (!mc.player.getInventory().getItem(slot).isEmpty()
                    && !ToolUtils.isTool(mc.player.getInventory().getItem(slot))) return slot;
            }
            return -1;
        }
        // Dump blocks that are not needed anymore
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        Tuple<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        // Do not dump tools
        for (int slot : invInformation.getA()) {
            if (!mc.player.getInventory().getItem(slot).isEmpty()
                && !ToolUtils.isTool(mc.player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private HashMap<Item, Integer> getRequiredItems() {
        //Calculate the next items to restock
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        forEachMapBlock((x, z, y, blockState, block) -> {
            if (blockState.isAir() && getActiveMapLayer()[x][z][y] != null) {
                // ChatUtils.info("Add material for: " + mapCorner.add(x, y, z).toShortString());
                Item material = block.asItem();
                if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                requiredItems.put(material, requiredItems.get(material) + 1);
                // Check if the item fits into inventory. If not, undo the last increment and return
                if (Utils.stacksRequired(requiredItems.values()) > availableSlots.size()) {
                    requiredItems.put(material, requiredItems.get(material) - 1);
                    return false;
                }
            }
            return true;
        });
        return requiredItems;
    }

    private void swapIntoHotbar(int slot) {
        Map<Item, Integer> itemSlot = new HashMap<>();
        Map<Item, Integer> itemDistance = new HashMap<>();
        Map<Item, Integer> itemFrequency = new HashMap<>();

        int targetSlot = availableHotBarSlots.get(0);

        // Scan hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemSlot.put(item, hotbarSlot);
                itemDistance.put(item, -1); // -1 = never used
                itemFrequency.put(item, 0);
            } else {
                targetSlot = hotbarSlot;
                break;
            }
        }

        // PRIORITY 1: empty slot → instant choice
        if (mc.player.getInventory().getItem(targetSlot).isEmpty()) {
            Utils.performSwap(slot, targetSlot);
            return;
        }

        // Get blocks until next use of items in hotbar
        AtomicReference<Integer> blockCounter = new AtomicReference<>(0);
        forEachMapBlock((x, z, y, blockState, block) -> {
            if (!Utils.isInInterval(workingInterval, x)) return true;
            blockCounter.set(blockCounter.get() + 1);

            if (blockState.isAir() || block == null) return true;
            Item item = block.asItem();

            if (itemDistance.containsKey(item) &&
                itemDistance.get(item) == -1) {
                itemDistance.put(item, blockCounter.get());
            }
            return true;
        });

        // Count frequency of items in hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemFrequency.put(item, itemFrequency.get(item) + 1);
            }
        }

        // Choose best candidate
        Item bestItem = null;
        int bestDistance = -2; // lower than -1
        int bestFrequency = -1;

        for (Item item : itemSlot.keySet()) {
            int distance = itemDistance.get(item); // -1 = never used
            int frequency = itemFrequency.get(item);

            boolean better = false;

            // PRIORITY 2: never used (-1)
            if (distance == -1 && bestDistance != -1) {
                better = true;
            }
            // PRIORITY 3: hotbar frequency
            else if (frequency > bestFrequency) {
                better = true;
            }
            // PRIORITY 4: distance to next use
            else if (frequency == bestFrequency && distance > bestDistance && bestDistance != -1) {
                better = true;
            }

            if (better) {
                bestItem = item;
                bestDistance = distance;
                bestFrequency = frequency;
            }
        }

        if (bestItem != null) {
            targetSlot = itemSlot.get(bestItem);
        }

        Utils.performSwap(slot, targetSlot);
    }

    private boolean isInventoryFull() {
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    // MapPrinter Interface for Slave Logic

    public void setLayer(boolean isUpper) {
        workOnUpper = isUpper;
    }

    public void setInterval(Tuple<Integer, Integer> interval) {
        if (debugPrints.get()) info("set interval to " + interval.getA() + " " + interval.getB());
        lastPlacedLine = interval.getA();
        workingInterval = interval;
    }

    public void pause() {
        if (!state.equals(State.AwaitSlaveContinue)) {
            oldState = state;
            state = State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
        }
    }

    public void start() {
        state = State.AwaitNBTFile;
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {
        info("ToDo");
        startBuilding();
    }

    public void slaveFinished(String slave) {
        if (switchedToMiningSlaves.contains(slave)) return;
        // Transform Placing Slave to Mining Slave
        if (SlaveSystem.slaves.size() <= 2 || (SlaveSystem.slaves.size() == 3 && slave == SlaveSystem.slaves.get(1))) {
            SlaveSystem.sendToSlave(slave, "interval:" + -1 + ":" + -1);
            SlaveSystem.sendToSlave(slave, "mine:" + -1);
            SlaveSystem.finishedSlavesDict.put(slave, false);
            SlaveSystem.activeSlavesDict.put(slave, true);
            switchedToMiningSlaves.add(slave);
        }
    }

    public void mineLine(int line) {
        if (line < 0) {
            initialSetup();
            if (!prepareNextMapFile()) return;
            state = State.Walking;
            nextBlockPos = null;
            if (Utils.hasTool(availableSlots)) {
                checkpoints.add(new Tuple(usedToolChest.getB(), new Tuple("usedToolChest", null)));
                checkpoints.add(new Tuple(getBestDumpStation(usedToolChest.getB()).getA(), new Tuple("dump", null)));
            } else {
                checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("dump", null)));
            }
            checkpoints.add(new Tuple(getBestDumpStation().getA(), new Tuple("calculateMiningRefill", null)));
            return;
        }
        if (!SlaveSystem.isSlave) {
            setInterval(new Tuple<>(line*2, workingInterval.getB()));
            minePath(workingInterval.getA() + 1);
        } else {
            setInterval(new Tuple<>(Math.max(workingInterval.getA(), 0), line*2 + 1));
            minePath(workingInterval.getB());
        }

        state = State.Mining;
    }

    private void updateTcpAddress() {
        if (mc == null || mc.level == null || !isActive()) return;
        if (tcpIp != null && tcpPort != null) {
            SlaveSystem.setTcpAddress(tcpIp.get(), tcpPort.get());
        }
    }

    public void addError(BlockPos relativeBlockPos) {}

    // Path Change Check

    private void warnPathChanged() {
        if (checkpoints != null && !activationReset.get()) {
            String reString = isActive() ? "re" : "";
            warning("The custom path is only applied if the module is " + reString + "started with Activation Reset enabled!");
        }
    }

    // Config System

    private void saveConfig(File configFile) {
        if (configFile == null) {
            error("No config file name selected.");
            return;
        }
        if (cartographyTable == null || finishedMapChest == null || dumpStations == null || lowerMapCorner == null
            || upperMapCorner == null || usedToolChest == null || materialDict.isEmpty() || toolSet.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "suppressed",
                cartographyTable,
                finishedMapChest,
                usedToolChest,
                mapMaterialChests,
                dumpStations,
                lowerMapCorner,
                upperMapCorner,
                materialDict,
                toolSet);
            Component configText = Component.literal(configFile.getName())
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully saved config to: ").withStyle(ChatFormatting.GRAY).append(configText));
        } catch (IOException e) {
            error("Failed to create config file.");
        }
    }

    private boolean loadConfig(File configFile) {
        if (configFile == null || !configFile.exists() || state == null) {
            warning("Could not find config file.");
            return false;
        }
        List<State> allowedStates = List.of(
            State.SelectingChests,
            State.SelectingFinishedMapChest,
            State.SelectingUsedToolChest,
            State.SelectingDumpStation,
            State.SelectingTable,
            State.SelectingLowerMapArea,
            State.SelectingUpperMapArea,
            State.AwaitRegisterResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return false;
        }

        try {
            ConfigDeserializer.ConfigData data =
                ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("suppressed")) {
                error("Config file is of type " + data.type + " and not 'suppressed'.");
                return false;
            }
            if (data.cartographyTable == null || data.finishedMapChest == null || data.dumpStations == null || data.mapCorner == null
                || data.upperMapCorner == null || data.usedToolChest == null || data.materialDict.isEmpty() || toolSet == null) {
                error("Config file is missing required data.");
                return false;
            }
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.usedToolChest = data.usedToolChest;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStations = data.dumpStations;
            this.lowerMapCorner = data.mapCorner;
            this.upperMapCorner = data.upperMapCorner;
            MapAreaCache.reset(lowerMapCorner, true);
            this.materialDict = data.materialDict;
            this.toolSet = data.toolSet;
            Component configText = Component.literal(configFile.getName())
                .withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully loaded config: ").withStyle(ChatFormatting.GRAY).append(configText));
            info("§aInteract with the §bStart Block §aon the §bMaster Account §ato start printing.");
            state = State.SelectingChests;
        } catch (IOException e) {
            error("Failed to read config file.");
        }
        return true;
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("§aAll nbt files finished");
                toggle();
            }
            return false;
        }
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }

        return true;
    }

    private boolean loadNBTFile() {
        try {
            NbtAccounter sizeTracker = new NbtAccounter(0x20000000L, 100);
            CompoundTag nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            ListTag paletteList = (ListTag) nbt.get("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            ListTag blockList = (ListTag) nbt.get("blocks");
            generateSuppressionLayers(blockList, blockPaletteDict);
            return true;
        } catch (Exception e) {
            warning("Failed to load NBT file: " + e.getMessage());
            e.printStackTrace();
            toggle();
            return false;
        }
    }

    private void generateSuppressionLayers(ListTag blockList, HashMap<Integer, Tuple<Block, Integer>> blockPaletteDict) {
        // Get the highest block of each column
        Tuple<Block, Integer>[][] absoluteHeightMap = new Tuple[128][129];
        for (int i = 0; i < blockList.size(); i++) {
            Optional<CompoundTag> blockOpt = blockList.getCompound(i);
            if (blockOpt.isEmpty()) continue;

            CompoundTag block = blockOpt.get();

            Optional<Integer> blockIdOpt = block.getInt("state");
            if (blockIdOpt.isEmpty() || !blockPaletteDict.containsKey(blockIdOpt.get())) continue;

            int blockId = blockIdOpt.get();

            ListTag pos = block.getList("pos").get();

            Optional<Integer> xOpt = pos.getInt(0);
            Optional<Integer> yOpt = pos.getInt(1);
            Optional<Integer> zOpt = pos.getInt(2);
            if (xOpt.isEmpty() || yOpt.isEmpty() || zOpt.isEmpty()) {
                continue;
            }

            int x = xOpt.get();
            int y = yOpt.get();
            int z = zOpt.get();
            if (absoluteHeightMap[x][z] == null || absoluteHeightMap[x][z].getB() < y) {
                Block material = blockPaletteDict.get(blockId).getA();
                absoluteHeightMap[x][z] = new Tuple<>(material, y);
            }
        }
        // Generate HeightDiffArray
        HeightDiff[][] heightDiffArray = new HeightDiff[128][128];
        for (int x = 0; x < absoluteHeightMap.length; x++) {
            for (int z = absoluteHeightMap[x].length-1; z > 0; z--) {
                int predecessorY = absoluteHeightMap[x][z - 1].getB();
                int currentY = absoluteHeightMap[x][z].getB();
                if (predecessorY > currentY) {
                    heightDiffArray[x][z-1] = HeightDiff.Up;
                } else if (predecessorY < currentY) {
                    heightDiffArray[x][z-1] = HeightDiff.Down;
                } else {
                    heightDiffArray[x][z-1] = HeightDiff.Even;
                }
            }
        }

        lowerMapLayer = new Block[128][129][2];
        upperMapLayer = new Block[128][129][2];
        boolean suppressed = false;
        for (int x = 0; x < heightDiffArray.length; x++) {
            int z = heightDiffArray[x].length-1;
            if (suppressed) {
                HeightDiff currentDiff = heightDiffArray[x][z];
                switch (currentDiff) {
                    case Up -> {
                        upperMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getA();
                        upperMapLayer[x][z][1] = fillerBlock.get();
                        if (supportBlocks.get()) upperMapLayer[x][z][0] = fillerBlock.get();
                    }
                    case Even -> {
                        lowerMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getA();
                    }
                    case Down -> {
                        lowerMapLayer[x][z+1][1] = absoluteHeightMap[x][z+1].getA();
                        if (supportBlocks.get()) lowerMapLayer[x][z+1][0] = fillerBlock.get();
                    }
                }
                z = z - 1;
            }
            suppressed = !suppressed;
            // Generate 2 blocks of a line per iteration
            while (z >= 0) {
                lowerMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getA();
                HeightDiff currentDiff = heightDiffArray[x][z];
                // Default values for north most row
                HeightDiff nextDiff = HeightDiff.Down;
                Block nextMaterial = fillerBlock.get();
                // Fetch info about next suppressed block if there is one
                if (z > 0) {
                    nextDiff = heightDiffArray[x][z-1];
                    nextMaterial = absoluteHeightMap[x][z].getA();
                }
                switch (currentDiff) {
                    case Up -> {
                        switch (nextDiff) {
                            case Up -> {
                                lowerMapLayer[x][z][1] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) {
                                    lowerMapLayer[x][z][0] = fillerBlock.get();
                                    upperMapLayer[x][z-1][0] = fillerBlock.get();
                                }
                            }
                            case Even -> {
                                lowerMapLayer[x][z][1] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][0] = fillerBlock.get();
                                if (supportBlocks.get()) lowerMapLayer[x][z][0] = fillerBlock.get();
                            }
                            case Down -> {
                                lowerMapLayer[x][z][1] = nextMaterial;
                                if (supportBlocks.get()) lowerMapLayer[x][z][0] = fillerBlock.get();
                            }
                        }
                    }
                    case Even -> {
                        switch (nextDiff) {
                            case Up -> {
                                lowerMapLayer[x][z][0] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Even -> {
                                lowerMapLayer[x][z][0] = nextMaterial;
                            }
                            case Down -> {
                                lowerMapLayer[x][z][0] = fillerBlock.get();
                                // No nextMaterial needed in last filler-row
                                if (z != 0) upperMapLayer[x][z][0] = nextMaterial;
                            }
                        }
                    }
                    case Down -> {
                        switch (nextDiff) {
                            case Up -> {
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Even -> {
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Down -> {
                                // No nextMaterial needed in last filler-row
                                if (z != 0) upperMapLayer[x][z][0] = nextMaterial;
                            }
                        }
                    }
                }
                // Missing non-suppressed support block, for last suppressed block
                if (z == 1 && heightDiffArray[x][0].equals(HeightDiff.Even)) {
                    lowerMapLayer[x][0][0] = fillerBlock.get();
                }

                z-=2;
            }
        }
    }

    // Rendering

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = new WTable();
        list.add(table);

        File configFolder = new File(mapFolder, "_configs");
        if (!configFolder.exists()) return table;

        table.add(theme.label("Configurations: "));
        // ---- Save config button ----
        WButton saveButton = table.add(theme.button("Save Config")).widget();
        saveButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Config",
                new File(configFolder, "suppressed-config.json").getAbsolutePath(),
                null,
                null
            );
            if (path != null) saveConfig(new File(path));
        };

        // ---- Load config button ----
        WButton loadButton = table.add(theme.button("Load Config")).widget();
        loadButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                "Load Config",
                new File(configFolder, "suppressed-config.json").getAbsolutePath(),
                null,
                null,
                false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WButton clearMapArea = table.add(theme.button("Clear Map Area & Start")).widget();
        clearMapArea.action = () -> {
            if (SlaveSystem.isSlave) {
                warning("Only the master account can do initiate the clear.");
                return;
            }
            if (SlaveSystem.slaves.isEmpty()) {
                warning("Need to register at least 1 slave before clearing.");
                return;
            }

            lowerMiningCommand = new ArrayList<>(IntStream.rangeClosed(0, 63).boxed().toList());
            upperMiningCommand = new ArrayList<>(IntStream.rangeClosed(0, 63).boxed().toList());

            // Prepare slaves for mining
            for (int i = SlaveSystem.slaves.size() - 1; i >= Math.max(0, SlaveSystem.slaves.size()-2); i--) {
                String slave = SlaveSystem.slaves.get(i);
                SlaveSystem.sendToSlave(slave, "interval:" + -1 + ":" + -1);
                SlaveSystem.sendToSlave(slave, "mine:" + -1);
                SlaveSystem.finishedSlavesDict.put(slave, false);
                SlaveSystem.activeSlavesDict.put(slave, true);
            }
            // Master slave helps mining
            setInterval(new Tuple<>(127, 127));
            mineLine(-1);
            // Prevent first mapFile from being moved
            mapFile = null;
            startedFiles.clear();
        };

        WButton closeConnection = table.add(theme.button("Close Connection")).widget();
        closeConnection.action = () -> {
            SlaveSystem.closeConnection();
            if (isActive()) toggle();
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);

        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, false);
        slaveController.rebuild();

        SlaveSystem.tableController = slaveController;
        return list;
    }

    @Override
    public String getInfoString() {
        if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (lowerMapCorner == null || !render.get()) return;
        try {
            event.renderer.box(lowerMapCorner.getX(), lowerMapCorner.getY(), lowerMapCorner.getZ(), lowerMapCorner.getX() + upperMapLayer.length, lowerMapCorner.getY(), lowerMapCorner.getZ() + upperMapLayer[0].length, color.get(), color.get(), ShapeMode.Lines, 0);
            if (upperMapCorner == null) return;
            event.renderer.box(upperMapCorner.getX(), upperMapCorner.getY(), upperMapCorner.getZ(), upperMapCorner.getX() + upperMapLayer.length, upperMapCorner.getY(), upperMapCorner.getZ() + upperMapLayer[0].length, color.get(), color.get(), ShapeMode.Lines, 0);

            if (renderNextBlockPos.get() && nextBlockPos != null) {
                event.renderer.box(nextBlockPos, color.get(), color.get(), ShapeMode.Lines, 0);
            }

            if (renderMap.get() && !(state.equals(State.Mining) || state.equals(State.AwaitBlockBreak))) {
                if (upperMapLayer != null && lowerMapLayer != null) {
                    for (int x = 0; x < lowerMapLayer.length; x++) {
                        for (int z = 0; z < lowerMapLayer[x].length; z++) {
                            for (int y = 0; y < lowerMapLayer[x][z].length; y++) {
                                Block material = lowerMapLayer[x][z][y];
                                BlockPos renderPos = new BlockPos(lowerMapCorner.getX() + x, lowerMapCorner.getY() + y, lowerMapCorner.getZ() + z);
                                if (material != null && (MapAreaCache.getCachedBlockState(renderPos) == null || MapAreaCache.getCachedBlockState(renderPos).isAir())) {
                                    Color renderColor = color.get();
                                    if (material.equals(fillerBlock.get())) renderColor = Color.BLUE;
                                    event.renderer.box(renderPos, renderColor, renderColor, ShapeMode.Lines, 0);
                                }
                            }
                        }
                    }
                    for (int x = 0; x < upperMapLayer.length; x++) {
                        for (int z = 0; z < upperMapLayer[x].length; z++) {
                            for (int y = 0; y < upperMapLayer[x][z].length; y++) {
                                Block material = upperMapLayer[x][z][y];
                                BlockPos renderPos = new BlockPos(upperMapCorner.getX() + x, upperMapCorner.getY() + y, upperMapCorner.getZ() + z);
                                if (material != null && (MapAreaCache.getCachedBlockState(renderPos) == null || MapAreaCache.getCachedBlockState(renderPos).isAir())) {
                                    Color renderColor = color.get();
                                    if (material.equals(fillerBlock.get())) renderColor = Color.BLUE;
                                    event.renderer.box(renderPos, renderColor, renderColor, ShapeMode.Lines, 0);
                                }
                            }
                        }
                    }
                }
            }

            ArrayList<Tuple<BlockPos, Vec3>> renderedPairs = new ArrayList<>();
            for (ArrayList<Tuple<BlockPos, Vec3>> list : materialDict.values()) {
                renderedPairs.addAll(list);
            }
            renderedPairs.addAll(mapMaterialChests);
            for (Tuple<BlockPos, Vec3> pair : renderedPairs) {
                if (renderChestPositions.get())
                    event.renderer.box(pair.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                if (renderOpenPositions.get()) {
                    Vec3 openPos = pair.getB();
                    event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
            }

            if (renderCheckpoints.get()) {
                for (Tuple<Vec3, Tuple<String, BlockPos>> pair : checkpoints) {
                    Vec3 cp = pair.getA();
                    event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.x + indicatorSize.get(), cp.y + indicatorSize.get(), cp.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
            }

            if (renderSpecialInteractions.get()) {
                if (usedToolChest != null) {
                    event.renderer.box(usedToolChest.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                    event.renderer.box(usedToolChest.getB().x - indicatorSize.get(), usedToolChest.getB().y - indicatorSize.get(), usedToolChest.getB().z - indicatorSize.get(), usedToolChest.getB().x() + indicatorSize.get(), usedToolChest.getB().y() + indicatorSize.get(), usedToolChest.getB().z() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
                if (cartographyTable != null) {
                    event.renderer.box(cartographyTable.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                    event.renderer.box(cartographyTable.getB().x - indicatorSize.get(), cartographyTable.getB().y - indicatorSize.get(), cartographyTable.getB().z - indicatorSize.get(), cartographyTable.getB().x + indicatorSize.get(), cartographyTable.getB().y + indicatorSize.get(), cartographyTable.getB().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
                if (finishedMapChest != null) {
                    event.renderer.box(finishedMapChest.getA(), color.get(), color.get(), ShapeMode.Lines, 0);
                    event.renderer.box(finishedMapChest.getB().x - indicatorSize.get(), finishedMapChest.getB().y - indicatorSize.get(), finishedMapChest.getB().z - indicatorSize.get(), finishedMapChest.getB().x + indicatorSize.get(), finishedMapChest.getB().y + indicatorSize.get(), finishedMapChest.getB().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
                if (dumpStations != null) {
                    for (int i = 0; i < dumpStations.size(); i++) {
                        Vec3 pos = dumpStations.get(i).getA();
                        event.renderer.box(pos.x - indicatorSize.get(), pos.y - indicatorSize.get(), pos.z - indicatorSize.get(), pos.x + indicatorSize.get(), pos.y + indicatorSize.get(), pos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                    }
                }
            }
        } catch (ConcurrentModificationException ignored) {}
    }

    private void forEachMapBlock(MapBlockAction action) {
        forEachMapBlock(action, false);
    }

    private void forEachMapBlock(MapBlockAction action, boolean completeMap) {
        int leftX = completeMap ? 0 : workingInterval.getA();
        int rightX = completeMap ? lowerMapLayer.length-1 : workingInterval.getB();
        for (int x = leftX; x <= rightX; x += linesPerRun.get()) {
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (!Utils.isInInterval(new Tuple<>(leftX, rightX), adjustedX)) break;

                    for (int y = 0; y < lowerMapLayer[adjustedX][z].length; y++) {
                        Block block = getActiveMapLayer()[adjustedX][z][y];
                        BlockPos pos = getActiveMapCorner().offset(adjustedX, y, z);
                        BlockState state = MapAreaCache.getCachedBlockState(pos);

                        if (!action.accept(adjustedX, z, y, state, block)) {
                            // Stop future iterations if false is returned
                            return;
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface MapBlockAction {
        boolean accept(int x, int z, int y,BlockState state, Block desiredBlock);
    }

    private enum State {
        SelectingLowerMapArea,
        SelectingUpperMapArea,
        SelectingTable,
        SelectingDumpStation,
        SelectingFinishedMapChest,
        SelectingUsedToolChest,
        SelectingChests,
        AwaitRegisterResponse,
        AwaitRestockResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitUsedToolChestResponse,
        AwaitUsedToolChestResponseDumpAll,
        AwaitCartographyResponse,
        AwaitNBTFile,
        AwaitBlockBreak,
        AwaitPlacedSuppression,
        AwaitMinedSuppression,
        AwaitMasterAllCleared,
        AwaitSlaveContinue,
        StandBy,
        Walking,
        Mining,
        Dumping,
        DumpingBlocks,
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum HeightDiff {
        Up,
        Even,
        Down
    }
}
