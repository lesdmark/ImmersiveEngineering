package blusunrize.immersiveengineering.api.tool;

import blusunrize.immersiveengineering.api.ApiUtils;
import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;


/**
 * @author BluSunrize - 17.08.2016
 *         A handler for custom conveyor types
 */
public class ConveyorHandler
{
	public static HashMap<ResourceLocation, Class<? extends IConveyorBelt>> classRegistry = new LinkedHashMap<ResourceLocation, Class<? extends IConveyorBelt>>();
	public static HashMap<ResourceLocation, Function<TileEntity, ? extends IConveyorBelt>> functionRegistry = new LinkedHashMap<ResourceLocation, Function<TileEntity, ? extends IConveyorBelt>>();
	public static HashMap<Class<? extends IConveyorBelt>, ResourceLocation> reverseClassRegistry = new LinkedHashMap<Class<? extends IConveyorBelt>, ResourceLocation>();
	public static Set<BiConsumer<Entity, IConveyorTile>> magnetSupressionFunctions = new HashSet<BiConsumer<Entity, IConveyorTile>>();
	public static Set<BiConsumer<Entity, IConveyorTile>> magnetSupressionReverse = new HashSet<BiConsumer<Entity, IConveyorTile>>();

	public static Block conveyorBlock;

	/**
	 * @param key           A unique ResourceLocation to identify the conveyor by
	 * @param conveyorClass the conveyor class
	 * @param function      a function used to create a new instance. Note that the TileEntity may be null for the inventory model. Handle accordingly.
	 */
	public static <T extends IConveyorBelt> boolean registerConveyorHandler(ResourceLocation key, Class<T> conveyorClass, Function<TileEntity, T> function)
	{
		if(classRegistry.containsKey(key))
			return false;
		classRegistry.put(key, conveyorClass);
		reverseClassRegistry.put(conveyorClass, key);
		functionRegistry.put(key, function);
		return true;
	}

	/**
	 * @return a new instance of the given conveyor type
	 */
	public static IConveyorBelt getConveyor(ResourceLocation key, @Nullable TileEntity tile)
	{
		Function<TileEntity, ? extends IConveyorBelt> func = functionRegistry.get(key);
		if(func != null)
			return func.apply(tile);
		return null;
	}

	/**
	 * @return an ItemStack with the given key written to NBT
	 */
	public static ItemStack getConveyorStack(String key)
	{
		ItemStack stack = new ItemStack(conveyorBlock);
		stack.setTagCompound(new NBTTagCompound());
		stack.getTagCompound().setString("conveyorType", key);
		return stack;
	}

	/**
	 * @return whether the given subtype key can be found at the location. Useful for multiblocks
	 */
	public static boolean isConveyor(World world, BlockPos pos, String key, @Nullable EnumFacing facing)
	{
		TileEntity tile = world.getTileEntity(pos);
		if(!(tile instanceof IConveyorTile))
			return false;
		if(facing != null && !facing.equals(((IConveyorTile) tile).getFacing()))
			return false;
		IConveyorBelt conveyor = ((IConveyorTile) tile).getConveyorSubtype();
		if(conveyor == null)
			return false;
		ResourceLocation rl = reverseClassRegistry.get(conveyor.getClass());
		return !(rl == null || !key.equalsIgnoreCase(rl.toString()));
	}

	/**
	 * registers a consumer/function to suppress magnets while they are on the conveyors
	 * the reversal function is optional, to revert possible NBT changes
	 * the tileentity parsed is an instanceof
	 */
	public static void registerMagnetSupression(BiConsumer<Entity, IConveyorTile> function, @Nullable BiConsumer<Entity, IConveyorTile> revert)
	{
		magnetSupressionFunctions.add(function);
		if(revert != null)
			magnetSupressionReverse.add(revert);
	}

	/**
	 * applies all registered magnets supressors to the entity
	 */
	public static void applyMagnetSupression(Entity entity, IConveyorTile tile)
	{
		if(entity != null)
			for(BiConsumer<Entity, IConveyorTile> func : magnetSupressionFunctions)
				func.accept(entity, tile);
	}

	/**
	 * applies all registered magnet supression removals
	 */
	public static void revertMagnetSupression(Entity entity, IConveyorTile tile)
	{
		if(entity != null)
			for(BiConsumer<Entity, IConveyorTile> func : magnetSupressionReverse)
				func.accept(entity, tile);
	}

	/**
	 * An interface for the external handling of conveyorbelts
	 */
	public interface IConveyorBelt
	{
		/**
		 * @return the string by which unique models would be cached. Override for additional appended information*
		 * The model class will also append to this key for rendered walls and facing
		 */
		default String getModelCacheKey(TileEntity tile, EnumFacing facing)
		{
			String key = reverseClassRegistry.get(this.getClass()).toString();
			key += "f" + facing.ordinal();
			key += "d" + getConveyorDirection().ordinal();
			key += "a" + (isActive(tile) ? 1 : 0);
			key += "w0" + (renderWall(tile, facing, 0) ? 1 : 0);
			key += "w1" + (renderWall(tile, facing, 1) ? 1 : 0);
			return key;
		}

		/**
		 * @return the transport direction; HORIZONTAL for flat conveyors, UP and DOWN for diagonals
		 */
		default ConveyorDirection getConveyorDirection()
		{
			return ConveyorDirection.HORIZONTAL;
		}

		/**
		 * Switch to the next possible ConveyorDirection
		 * @return true if renderupdate should happen
		 */
		boolean changeConveyorDirection();

		/**
		 * Set the ConveyorDirection to given
		 *
		 * @return false if the direction is not possible for this conveyor
		 */
		boolean setConveyorDirection(ConveyorDirection dir);

		/**
		 * @return false if the conveyor is deactivated (for instance by a redstone signal)
		 */
		boolean isActive(TileEntity tile);

		/**
		 * @param wall 0 is left, 1 is right
		 * @return whether the wall should be drawn on the model. Also used for they cache key
		 */
		default boolean renderWall(TileEntity tile, EnumFacing facing, int wall)
		{
			if(getConveyorDirection() != ConveyorDirection.HORIZONTAL)
				return true;
			EnumFacing side = wall == 0 ? facing.rotateYCCW() : facing.rotateY();
			BlockPos pos = tile.getPos().offset(side);
			TileEntity te = tile.getWorld().getTileEntity(pos);
			if(te instanceof IConveyorTile && ((IConveyorTile) te).getFacing() == side.getOpposite())
				return false;
			else
			{
				te = tile.getWorld().getTileEntity(pos.add(0, -1, 0));
				if(te instanceof IConveyorTile && ((IConveyorTile) te).getFacing() == side.getOpposite() && ((IConveyorTile) te).getConveyorSubtype() != null && ((IConveyorTile) te).getConveyorSubtype().getConveyorDirection() == ConveyorDirection.UP)
					return false;
			}
			return true;
		}

		/**
		 * a rough indication of where this conveyor will transport things. Relevant for vertical conveyors, to see if they need to render the groundpiece below them.
		 */
		default Vec3d sigTransportDirection(TileEntity conveyorTile, EnumFacing facing)
		{
			return new Vec3d(facing.getDirectionVec()).addVector(0, getConveyorDirection() == ConveyorDirection.UP ? 1 : getConveyorDirection() == ConveyorDirection.DOWN ? -1 : 0, 0);
		}

		/**
		 * @return a vector representing the movement applied to the entity
		 */
		default Vec3d getDirection(TileEntity conveyorTile, Entity entity, EnumFacing facing)
		{
			ConveyorDirection conveyorDirection = getConveyorDirection();
			BlockPos pos = conveyorTile.getPos();

			double vBase = 1.15;
			double vX = 0.1 * vBase * facing.getFrontOffsetX();
			double vY = entity.motionY;
			double vZ = 0.1 * vBase * facing.getFrontOffsetZ();

			if(conveyorDirection == ConveyorDirection.UP)
				vY = 0.17D * vBase;
			else if(conveyorDirection == ConveyorDirection.DOWN)
				vY = -0.07000000000000001D * vBase;

			if(conveyorDirection != ConveyorDirection.HORIZONTAL)
				entity.onGround = false;

			if(facing == EnumFacing.WEST || facing == EnumFacing.EAST)
			{
				if(entity.posZ > pos.getZ() + 0.65D)
					vZ = -0.1D * vBase;
				else if(entity.posZ < pos.getZ() + 0.35D)
					vZ = 0.1D * vBase;
			} else if(facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH)
			{
				if(entity.posX > pos.getX() + 0.65D)
					vX = -0.1D * vBase;
				else if(entity.posX < pos.getX() + 0.35D)
					vX = 0.1D * vBase;
			}

			return new Vec3d(vX, vY, vZ);
		}

		default void onEntityCollision(TileEntity tile, Entity entity, EnumFacing facing)
		{
			if(!isActive(tile))
				return;
			BlockPos pos = tile.getPos();
			ConveyorDirection conveyorDirection = getConveyorDirection();
			float heightLimit = conveyorDirection == ConveyorDirection.HORIZONTAL ? .25f : 1f;
			if(entity != null && !entity.isDead && !(entity instanceof EntityPlayer && entity.isSneaking()) && entity.posY - pos.getY() >= 0 && entity.posY - pos.getY() < heightLimit)
			{
				Vec3d vec = this.getDirection(tile, entity, facing);
				if(entity.fallDistance < 3)
					entity.fallDistance = 0;
				entity.motionX = vec.xCoord;
				entity.motionY = vec.yCoord;
				entity.motionZ = vec.zCoord;
				double distX = Math.abs(pos.offset(facing).getX() + .5 - entity.posX);
				double distZ = Math.abs(pos.offset(facing).getZ() + .5 - entity.posZ);
				double treshold = .9;
				boolean contact = facing.getAxis() == Axis.Z ? distZ < treshold : distX < treshold;
				if(contact && conveyorDirection == ConveyorDirection.UP && !tile.getWorld().getBlockState(pos.offset(facing).up()).isFullBlock())
				{
					double move = .4;
					entity.setPosition(entity.posX + move * facing.getFrontOffsetX(), entity.posY + 1 * move, entity.posZ + move * facing.getFrontOffsetZ());
				}
				if(!contact)
					ConveyorHandler.applyMagnetSupression(entity, (IConveyorTile) tile);
				else if(!(tile.getWorld().getTileEntity(tile.getPos().offset(facing)) instanceof IConveyorTile))
					ConveyorHandler.revertMagnetSupression(entity, (IConveyorTile) tile);

				if(entity instanceof EntityItem)
				{
					((EntityItem) entity).setNoDespawn();
					handleInsertion(tile, (EntityItem) entity, facing, conveyorDirection, distX, distZ);
				}
			}
		}

		default void handleInsertion(TileEntity tile, EntityItem entity, EnumFacing facing, ConveyorDirection conDir, double distX, double distZ)
		{
			TileEntity inventoryTile = tile.getWorld().getTileEntity(tile.getPos().offset(facing).add(0, (conDir == ConveyorDirection.UP ? 1 : conDir == ConveyorDirection.DOWN ? -1 : 0), 0));
			boolean contact = facing.getAxis() == Axis.Z ? distZ < .7 : distX < .7;
			if(!tile.getWorld().isRemote)
			{
				if(contact && inventoryTile != null && !(inventoryTile instanceof IConveyorTile))
				{
					ItemStack stack = entity.getEntityItem();
					if(stack != null)
					{
						ItemStack ret = ApiUtils.insertStackIntoInventory(inventoryTile, stack, facing.getOpposite());
						if(ret == null)
							entity.setDead();
						else if(ret.stackSize < stack.stackSize)
							entity.setEntityItemStack(ret);
					}
				}
			}
		}

		AxisAlignedBB conveyorBounds = new AxisAlignedBB(0, 0, 0, 1, .125f, 1);
		AxisAlignedBB highConveyorBounds = new AxisAlignedBB(0, 0, 0, 1, 1.125f, 1);

		default AxisAlignedBB getSelectionBox(TileEntity tile, EnumFacing facing)
		{
			return getConveyorDirection() == ConveyorDirection.HORIZONTAL ? conveyorBounds : highConveyorBounds;
		}

		default List<AxisAlignedBB> getColisionBoxes(TileEntity tile, EnumFacing facing)
		{
			return Lists.newArrayList(conveyorBounds);
		}

		NBTTagCompound writeConveyorNBT();

		void readConveyorNBT(NBTTagCompound nbt);

		@SideOnly(Side.CLIENT)
		default Matrix4f modifyBaseRotationMatrix(Matrix4f matrix, @Nullable TileEntity tile, EnumFacing facing)
		{
			return matrix;
		}

		@SideOnly(Side.CLIENT)
		ResourceLocation getActiveTexture();

		@SideOnly(Side.CLIENT)
		ResourceLocation getInactiveTexture();

		@SideOnly(Side.CLIENT)
		default Set<BakedQuad> modifyQuads(Set<BakedQuad> baseModel, TileEntity tile, EnumFacing facing)
		{
			return baseModel;
		}
	}

	public enum ConveyorDirection
	{
		HORIZONTAL,
		UP,
		DOWN
	}

	/**
	 * This interface solely exists to mark a tile as conveyor, and have it ignored for insertion
	 */
	public interface IConveyorTile
	{
		EnumFacing getFacing();

		IConveyorBelt getConveyorSubtype();

		void setConveyorSubtype(IConveyorBelt conveyor);
	}
}