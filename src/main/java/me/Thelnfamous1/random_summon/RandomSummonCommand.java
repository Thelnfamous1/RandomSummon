package me.Thelnfamous1.random_summon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.Optional;
import java.util.function.Function;

public class RandomSummonCommand {

    private static final Dynamic2CommandExceptionType UNKNOWN_TAG = new Dynamic2CommandExceptionType((tag, registry) ->
            Component.translatable("commands.forge.tags.error.unknown_tag", tag, registry));
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(Commands.literal("randomsummon")
                .requires((sourceStack) -> sourceStack.hasPermission(2))
                .then(Commands.argument("tag", ResourceLocationArgument.id())
                        .suggests(suggestFromRegistry(r -> r.getTagNames().map(TagKey::location)::iterator))
                        .executes((context) -> spawnEntity(context.getSource(), ResourceLocationArgument.getId(context, "tag"), context.getSource().getPosition(), new CompoundTag(), true))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes((context) -> spawnEntity(context.getSource(), ResourceLocationArgument.getId(context, "tag"), Vec3Argument.getVec3(context, "pos"), new CompoundTag(), true))
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes((context) -> spawnEntity(context.getSource(), ResourceLocationArgument.getId(context, "tag"), Vec3Argument.getVec3(context, "pos"), CompoundTagArgument.getCompoundTag(context, "nbt"), false))))));
    }

    private static SuggestionProvider<CommandSourceStack> suggestFromRegistry(final Function<Registry<?>, Iterable<ResourceLocation>> namesFunction) {
        return (ctx, builder) -> Optional.of(Registry.ENTITY_TYPE)
                .map(registry -> {
                    SharedSuggestionProvider.suggestResource(namesFunction.apply(registry), builder);
                    return builder.buildFuture();
                })
                .orElseGet(builder::buildFuture);
    }

    @SuppressWarnings("unchecked")
    private static <O> O cast(final Object input)
    {
        return (O) input;
    }

    private static int spawnEntity(CommandSourceStack pSource, ResourceLocation tagId, Vec3 pPos, CompoundTag pNbt, boolean pRandomizeProperties) throws CommandSyntaxException {
        BlockPos blockpos = new BlockPos(pPos);
        if (!Level.isInSpawnableBounds(blockpos)) {
            throw INVALID_POSITION.create();
        } else {
            CompoundTag nbtCopy = pNbt.copy();

            final TagKey<?> tagKey = TagKey.create(Registry.ENTITY_TYPE_REGISTRY, tagId);

            HolderSet.Named<EntityType<?>> tag = Registry.ENTITY_TYPE.getTag(cast(tagKey))
                    .orElseThrow(() -> UNKNOWN_TAG.create(tagKey.location(), Registry.ENTITY_TYPE_REGISTRY.location()));

            Optional<Holder<EntityType<?>>> randomType = tag.getRandomElement(pSource.getLevel().random);
            if(randomType.isEmpty()) throw ERROR_FAILED.create();

            ResourceLocation randomTypeId = EntityType.getKey(randomType.get().value());

            nbtCopy.putString("id", randomTypeId.toString());
            ServerLevel level = pSource.getLevel();
            Entity entity = EntityType.loadEntityRecursive(nbtCopy, level, (e) -> {
                e.moveTo(pPos.x, pPos.y, pPos.z, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity == null) {
                throw ERROR_FAILED.create();
            } else {
                if (pRandomizeProperties && entity instanceof Mob mob) {
                    if (!ForgeEventFactory.doSpecialSpawn(mob, pSource.getLevel(), (float)mob.getX(), (float)mob.getY(), (float)mob.getZ(), null, MobSpawnType.COMMAND))
                        mob.finalizeSpawn(pSource.getLevel(), pSource.getLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
                }

                if (!level.tryAddFreshEntityWithPassengers(entity)) {
                    throw ERROR_DUPLICATE_UUID.create();
                } else {
                    pSource.sendSuccess(Component.translatable("commands.summon.success", entity.getDisplayName()), true);
                    return 1;
                }
            }
        }
    }
}
