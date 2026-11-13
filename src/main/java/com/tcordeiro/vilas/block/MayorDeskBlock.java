package com.tcordeiro.vilas.block;

import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.ui.TownDialog;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesa do Prefeito: o posto de trabalho do Prefeito (um aldeão desempregado que a apanhe passa a Prefeito) e o quadro de missões
 * (clique direito abre o mesmo diálogo que falar com o Prefeito). Só pode haver UMA por vila: não se põe dentro do raio de uma
 * vila que já tenha mesa. A entidade ({@link MayorDeskBlockEntity}) cuida do nível da vila e do alarme.
 */
public class MayorDeskBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(1, 0, 1, 15, 12, 15));

	public MayorDeskBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		// uma Prefeitura por vila (só o servidor sabe onde estão as vilas; o cliente corrige-se sozinho)
		if (ctx.getLevel() instanceof ServerLevel level && !VillageManager.canPlaceDesk(level, ctx.getClickedPos())) {
			if (ctx.getPlayer() instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("tvilas.desk.duplicate"), true);
			return null;
		}
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	/** 0.3.0 (M15): quem põe a Mesa fica dono da área cívica (com os amigos que juntar); nas Mesas geradas, só os operadores. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel sl && placer instanceof ServerPlayer p && !(p instanceof net.fabricmc.fabric.api.entity.FakePlayer)) {
			Village v = VillageManager.byDesk(sl, pos);
			if (v == null) v = VillageManager.found(sl, pos, com.tcordeiro.vilas.VilasConfig.get().radius());
			if (v != null && v.civic.owner == null) {
				v.civic.owner = p.getUUID();
				com.tcordeiro.vilas.village.VillageData.get(sl).changed();
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			Village v = VillageManager.byDesk(sl, pos);
			if (v == null) v = VillageManager.found(sl, pos, com.tcordeiro.vilas.VilasConfig.get().radius());
			if (v != null) {
				Component back = com.tcordeiro.vilas.village.VillageEconomy.recallMayor(sl, v); // C6: o Prefeito levado volta pela Mesa
				if (back != null) sp.sendSystemMessage(back);
				TownDialog.open(sp, sl, v);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MayorDeskBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) return null;
		return createTickerHelper(type, VilasBlocks.MAYOR_DESK_BE, MayorDeskBlockEntity::serverTick);
	}
}
