package mchorse.emoticons.api.animation.model;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import mchorse.emoticons.common.emotes.Emote;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMeshConfig;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorController;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorHeldItemConfig;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.mclib.client.render.RenderLightmap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


@SideOnly(Side.CLIENT)
public class AnimatorEmoticonsController extends AnimatorController
{
    private static final Map<String, ResourceLocation> ARMOR_TEXTURE_RES_MAP = Maps.<String, ResourceLocation>newHashMap();

    /**
     * Default armor models, the same ones vanilla's LayerBipedArmor passes to
     * Forge. If Forge hands back one of these, the item has no custom model.
     */
    private static final ModelBiped DEFAULT_LEGS = new ModelBiped(0.5F);
    private static final ModelBiped DEFAULT_ARMOR = new ModelBiped(1.0F);

    /**
     * Custom armor model classes that were already logged (log once per class)
     */
    private static final Set<Class> LOGGED_MODELS = new HashSet<Class>();

    /**
     * Armor pieces that come with their own model (skirts, wings, etc.). They
     * can't be drawn with the BOBJ armor meshes, so they're drawn separately
     * on top of the bones.
     */
    private final Map<EntityEquipmentSlot, CustomArmor> customArmor = new EnumMap<EntityEquipmentSlot, CustomArmor>(EntityEquipmentSlot.class);

    private static class CustomArmor
    {
        public EntityEquipmentSlot slot;
        public ModelBiped model;
        public ResourceLocation texture;
        public int color;
    }

    public ItemStack itemSlot = ItemStack.EMPTY;
    public float itemSlotScale = 0;

    public AnimatorEmoticonsController(String animationName, NBTTagCompound userData)
    {
        super(animationName, userData);
    }

    public void render(Emote emote, EntityLivingBase entity, double x, double y, double z, int i, float partialTicks)
    {
        this.e = emote;
        this.render(entity, x, y, z, i, partialTicks);
        this.e = null;
    }

    /**
     * Render current animation
     */
    @Override
    public void renderAnimation(EntityLivingBase entity, AnimationMesh mesh, float yaw, float partialTicks)
    {
        this.updateArmor(entity);

        super.renderAnimation(entity, mesh, yaw, partialTicks);

        BOBJArmature original = mesh.getArmature();
        BOBJArmature armature = this.animator.useArmature(original);

        this.renderCustomArmor(entity, armature, partialTicks);

        if (this.e != null && this.emote != null)
        {
            this.e.renderEmote(entity, armature, this, (int) this.emote.getTick(0), partialTicks);
        }
    }

    /**
     * Render hand held items and skin layer
     */
    @Override
    protected void renderItems(EntityLivingBase entity, BOBJArmature armature)
    {
        if (!this.userConfig.renderHeldItems)
        {
            return;
        }

        float scale = this.userConfig.scaleItems;

        ItemStack mainItem = entity.getHeldItemMainhand();
        ItemStack offItem = entity.getHeldItemOffhand();

        if (!this.itemSlot.isEmpty())
        {
            if (this.itemSlotScale > 0)
            {
                for (AnimatorHeldItemConfig itemConfig : this.userConfig.rightHands.values())
                {
                    this.renderItem(entity, this.itemSlot, armature, itemConfig, TransformType.THIRD_PERSON_RIGHT_HAND, scale * this.itemSlotScale);
                }
            }
        }
        else if (!mainItem.isEmpty() && this.userConfig.rightHands != null)
        {
            for (AnimatorHeldItemConfig itemConfig : this.userConfig.rightHands.values())
            {
                this.renderItem(entity, mainItem, armature, itemConfig, TransformType.THIRD_PERSON_RIGHT_HAND, scale);
            }
        }

        if (!offItem.isEmpty() && this.userConfig.leftHands != null)
        {
            for (AnimatorHeldItemConfig itemConfig : this.userConfig.leftHands.values())
            {
                this.renderItem(entity, offItem, armature, itemConfig, TransformType.THIRD_PERSON_LEFT_HAND, scale);
            }
        }
    }

    /**
     * Update armor slots
     */
    private void updateArmor(EntityLivingBase entity)
    {
        this.customArmor.clear();

        this.updateArmorSlot("armor_helmet", entity, EntityEquipmentSlot.HEAD);
        this.updateArmorSlot("armor_chest", entity, EntityEquipmentSlot.CHEST);
        this.updateArmorSlot("armor_leggings", entity, EntityEquipmentSlot.LEGS);
        this.updateArmorSlot("armor_feet", entity, EntityEquipmentSlot.FEET);
    }

    /**
     * Update separate armor slots
     */
    private void updateArmorSlot(String id, EntityLivingBase entity, EntityEquipmentSlot slot)
    {
        AnimationMeshConfig config = this.userConfig.meshes.get(id);

        if (config == null)
        {
            return;
        }

        if (this.userData.hasKey("Meshes", Constants.NBT.TAG_COMPOUND) && this.userData.getCompoundTag("Meshes").hasKey(id))
        {
            return;
        }

        ItemStack stack = entity.getItemStackFromSlot(slot);

        if (stack.getItem() instanceof ItemArmor)
        {
            ItemArmor item = (ItemArmor) stack.getItem();

            config.visible = true;
            config.texture = this.getArmorResource(entity, stack, slot, null);
            config.color = 0xffffffff;

            if (item.hasOverlay(stack))
            {
                config.color = item.getColor(stack);
            }

            /* Items with a custom armor model (skirts from other mods and
             * so on) can't use the BOBJ armor mesh: it would look like
             * regular pants. Hide the mesh and draw the item's model. */
            ModelBiped custom = this.getCustomModel(entity, stack, slot);

            if (custom != null)
            {
                CustomArmor armor = new CustomArmor();

                armor.slot = slot;
                armor.model = custom;
                armor.texture = config.texture;
                armor.color = config.color;

                this.customArmor.put(slot, armor);

                config.visible = false;

                if (LOGGED_MODELS.add(custom.getClass()))
                {
                    System.out.println("[Emoticons] Custom armor model: " + custom.getClass().getName() + " (" + stack.getItem().getRegistryName() + ")");
                }
            }
        }
        else
        {
            config.visible = false;
            config.color = 0xffffffff;
        }
    }

    /**
     * Ask Forge for the armor model of the item. Returns null when the item
     * doesn't override the default model.
     */
    private ModelBiped getCustomModel(EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot)
    {
        ModelBiped def = isLegSlot(slot) ? DEFAULT_LEGS : DEFAULT_ARMOR;
        ModelBiped model = net.minecraftforge.client.ForgeHooksClient.getArmorModel(entity, stack, slot, def);

        return model == def ? null : model;
    }

    /**
     * Draw armor pieces that have a custom model. Every body part of the
     * model is attached to the matching bone, using the same skinning matrix
     * that the BOBJ meshes use, so it follows the current animation.
     */
    private void renderCustomArmor(EntityLivingBase entity, BOBJArmature armature, float partialTicks)
    {
        if (this.customArmor.isEmpty() || entity.isInvisible())
        {
            return;
        }

        boolean flag = RenderLightmap.set(entity, partialTicks);

        for (CustomArmor armor : this.customArmor.values())
        {
            ModelBiped model = armor.model;

            this.prepareModel(model, armor.slot, entity);

            Minecraft.getMinecraft().renderEngine.bindTexture(armor.texture);
            GlStateManager.color((armor.color >> 16 & 255) / 255.0F, (armor.color >> 8 & 255) / 255.0F, (armor.color & 255) / 255.0F, 1);

            this.renderPart(model.bipedHead, "head", armature);
            this.renderPart(model.bipedHeadwear, "head", armature);
            this.renderPart(model.bipedBody, "body", armature);
            this.renderPart(model.bipedRightArm, "right_arm", armature);
            this.renderPart(model.bipedLeftArm, "left_arm", armature);
            this.renderPart(model.bipedRightLeg, "right_leg", armature);
            this.renderPart(model.bipedLeftLeg, "left_leg", armature);
        }

        GlStateManager.color(1, 1, 1, 1);

        if (flag)
        {
            RenderLightmap.unset();
        }
    }

    /**
     * Put the model into its rest pose (it may be shared with other
     * renderers that left it rotated) and show only the parts of the slot,
     * same as vanilla's LayerBipedArmor does.
     */
    private void prepareModel(ModelBiped model, EntityEquipmentSlot slot, EntityLivingBase entity)
    {
        model.isSneak = false;
        model.isRiding = false;
        model.isChild = false;
        model.swingProgress = 0;
        model.leftArmPose = ModelBiped.ArmPose.EMPTY;
        model.rightArmPose = ModelBiped.ArmPose.EMPTY;

        model.setRotationAngles(0, 0, 0, 0, 0, 0.0625F, entity);

        this.resetRotation(model.bipedHead);
        this.resetRotation(model.bipedHeadwear);
        this.resetRotation(model.bipedBody);
        this.resetRotation(model.bipedRightArm);
        this.resetRotation(model.bipedLeftArm);
        this.resetRotation(model.bipedRightLeg);
        this.resetRotation(model.bipedLeftLeg);

        model.setVisible(false);

        switch (slot)
        {
            case HEAD:
                model.bipedHead.showModel = true;
                model.bipedHeadwear.showModel = true;
                break;
            case CHEST:
                model.bipedBody.showModel = true;
                model.bipedRightArm.showModel = true;
                model.bipedLeftArm.showModel = true;
                break;
            case LEGS:
                model.bipedBody.showModel = true;
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            case FEET:
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            default:
                break;
        }
    }

    private void resetRotation(ModelRenderer part)
    {
        part.rotateAngleX = part.rotateAngleY = part.rotateAngleZ = 0;
    }

    /**
     * Render a part of the vanilla biped model on top of a bone.
     *
     * The BOBJ model is Y-up with the front looking toward +Z, 1 unit = 16
     * pixels and the feet at Y = 0. The biped model is Y-down, with the
     * front looking toward -Z and the feet at Y = 24 pixels. Rendering with
     * scale 0.0625 gives blocks, so Y and Z get flipped and the model is
     * lifted by 1.5 blocks.
     */
    private void renderPart(ModelRenderer part, String boneName, BOBJArmature armature)
    {
        BOBJBone bone = armature.bones.get(boneName);

        if (part == null || bone == null || !part.showModel || part.isHidden)
        {
            return;
        }

        GlStateManager.pushMatrix();

        this.setupMatrix(armature.matrices[bone.index]);
        GlStateManager.translate(0, 1.5F, 0);
        GlStateManager.scale(1, -1, -1);

        part.render(0.0625F);

        GlStateManager.popMatrix();
    }

    /**
     * More generic ForgeHook version of the above function, it allows for Items to have more control over what texture they provide.
     *
     * @param entity Entity wearing the armor
     * @param stack ItemStack for the armor
     * @param slot Slot ID that the item is in
     * @param type Subtype, can be null or "overlay"
     * @return ResourceLocation pointing at the armor's texture
     */
    private ResourceLocation getArmorResource(net.minecraft.entity.Entity entity, ItemStack stack, EntityEquipmentSlot slot, String type)
    {
        ItemArmor item = (ItemArmor) stack.getItem();
        String texture = item.getArmorMaterial().getName();
        String domain = "minecraft";
        int idx = texture.indexOf(':');
        if (idx != -1)
        {
            domain = texture.substring(0, idx);
            texture = texture.substring(idx + 1);
        }
        String s1 = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture, (isLegSlot(slot) ? 2 : 1), type == null ? "" : String.format("_%s", type));

        s1 = net.minecraftforge.client.ForgeHooksClient.getArmorTexture(entity, stack, s1, slot, type);
        ResourceLocation resourcelocation = ARMOR_TEXTURE_RES_MAP.get(s1);

        if (resourcelocation == null)
        {
            resourcelocation = new ResourceLocation(s1);
            ARMOR_TEXTURE_RES_MAP.put(s1, resourcelocation);
        }

        return resourcelocation;
    }

    private boolean isLegSlot(EntityEquipmentSlot slotIn)
    {
        return slotIn == EntityEquipmentSlot.LEGS;
    }
}