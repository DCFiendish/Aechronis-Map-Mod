package com.dcfiendish.aechronismapmod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** EditBox for the world-map search bar — Enter triggers a search, everything else is
 *  normal text-field behavior (see AechronisMapSearch, AechronisGuiMapSearchMixin). */
public class AechronisSearchBox extends EditBox {

    public AechronisSearchBox(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height, Component.literal("Search"));
        setHint(Component.literal("Search town or node id..."));
        setMaxLength(64);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            AechronisMapSearch.searchAndWaypoint(getValue());
            return true;
        }
        return super.keyPressed(event);
    }
}
