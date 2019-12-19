package me.zeroeightsix.kami.module;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.modules.ClickGUI;
import me.zeroeightsix.kami.util.ClassFinder;
import me.zeroeightsix.kami.util.EntityUtil;
import me.zeroeightsix.kami.util.KamiTessellator;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by 086 on 23/08/2017.
 */
public class ModuleManager {

    public static List<Module> modules = new ArrayList<>();

    /**
     * Lookup map for getting by class
     */
    static HashMap<Class<? extends Module>, Integer> lookup = new HashMap<>();

    public static void updateLookup() {
        lookup.clear();
        for (int i = 0; i < modules.size(); i++) {
            lookup.put(modules.get(i).getClass(), i);
        }
    }

    public static void initialize() {
        Set<Class> classList = ClassFinder.findClasses(ClickGUI.class.getPackage().getName(), Module.class);
        classList.forEach(aClass -> {
            try {
                Module module = (Module) aClass.getConstructor().newInstance();
                modules.add(module);
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
                System.err.println("Couldn't initiate module " + aClass.getSimpleName() + "! Err: " + e.getClass().getSimpleName() + ", message: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Couldn't initiate module " + aClass.getSimpleName() + "! Err: " + e.getClass().getSimpleName() + ", message: " + e.getMessage());
            }
        });
        KamiMod.log.info("Modules initialised");
        getModules().sort(Comparator.comparing(Module::getOriginalName));
        updateLookup();
    }

    public static void onUpdate() {
        modules.stream().filter(module -> module.alwaysListening || module.isEnabled()).forEach(Module::onUpdate);
    }

    public static void onRender() {
        modules.stream().filter(module -> module.alwaysListening || module.isEnabled()).forEach(Module::onRender);
    }

    public static void onWorldRender(RenderWorldLastEvent event) {
        Minecraft.getMinecraft().profiler.startSection("kami");

        Minecraft.getMinecraft().profiler.startSection("setup");
//        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableDepth();

        GlStateManager.glLineWidth(1f);
        Vec3d renderPos = EntityUtil.getInterpolatedPos(Wrapper.getPlayer(), event.getPartialTicks());

        RenderEvent e = new RenderEvent(KamiTessellator.INSTANCE, renderPos);
        e.resetTranslation();
        Minecraft.getMinecraft().profiler.endSection();

        modules.stream().filter(module -> module.alwaysListening || module.isEnabled()).forEach(module -> {
            Minecraft.getMinecraft().profiler.startSection(module.getOriginalName());
            module.onWorldRender(e);
            Minecraft.getMinecraft().profiler.endSection();
        });

        Minecraft.getMinecraft().profiler.startSection("release");
        GlStateManager.glLineWidth(1f);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
//        GlStateManager.popMatrix();
        KamiTessellator.releaseGL();
        Minecraft.getMinecraft().profiler.endSection();
    }

    public static void onBind(int eventKey) {
        if (eventKey == 0) return; // if key is the 'none' key (stuff like mod key in i3 might return 0)
        modules.forEach(module -> {
            if (module.getBind().isDown(eventKey)) {
                module.toggle();
            }
        });
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static Module getModule(Class<? extends Module> clazz) {
        return modules.get(lookup.get(clazz));
    }

    /**
     * @deprecated Use `getModule(Class<? extends Module>)` instead
     */
    @Deprecated
    public static Module getModule(String name) {
        for (Module module : modules) {
            if (module.getClass().getSimpleName().equalsIgnoreCase(name) || module.getOriginalName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        throw new ModuleNotFoundException("getModuleByName(String) failed. Check spelling.");
    }

    public static boolean isModuleEnabled(String moduleName) {
        return getModule(moduleName).isEnabled();
    }
}
class ModuleNotFoundException extends IllegalArgumentException {

    public ModuleNotFoundException(String s) {
        super(s);
    }
}