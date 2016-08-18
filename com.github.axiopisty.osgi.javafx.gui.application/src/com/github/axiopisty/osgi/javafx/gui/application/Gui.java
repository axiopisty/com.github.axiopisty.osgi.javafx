package com.github.axiopisty.osgi.javafx.gui.application;

import org.osgi.service.component.annotations.Component;

import java.net.URL;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import com.github.axiopisty.osgi.javafx.launcher.api.StageService;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import osgi.enroute.debug.api.Debug;

@Component(
	name="com.github.axiopisty.osgi.javafx.gui",
	service = Gui.class,
	property = {
		Debug.COMMAND_SCOPE + "=gui",
		Debug.COMMAND_FUNCTION + "=show",
		Debug.COMMAND_FUNCTION + "=hide",
	},
	immediate = true
)
public class Gui {

	@Reference
	StageService stageService;
	
	@Activate
	void activate() {
		Platform.runLater(() -> {
			Stage primaryStage = stageService.getInstance();
			primaryStage.setTitle("Hello World!");
			Button btn = new Button();
			btn.setText("Click me!");
			btn.setOnAction(event -> {System.out.println("The button works, but modena.css is not loading properly.");});
			 
			StackPane root = new StackPane();
			root.getChildren().add(btn);
			Scene scene = new Scene(root, 300, 250);
			javafxOsgiHack(scene);
			primaryStage.setScene(scene);
		});
		show();
	}
	
	/**
	 * This hack is used to get the CSS styles to work appropriately inside the OSGi environment.
	 * I'm not sure why this is needed, but without it, the css file is not loaded and the 
	 * following warning is displayed in the console:
	 * 
	 * com.sun.javafx.css.StyleManager loadStylesheetUnPrivileged
	 * WARNING: Resource "com/sun/javafx/scene/control/skin/modena/modena.css" not found.
	 * 
	 * @param scene
	 */
	private void javafxOsgiHack(Scene scene) {
		Bundle systemBundle = FrameworkUtil.getBundle(getClass()).getBundleContext().getBundle(0);
		URL modena = systemBundle.getResource("com/sun/javafx/scene/control/skin/modena/modena.css");
		scene.getStylesheets().add(modena.toExternalForm());
	}
	
	@Deactivate
	void deactivate() {
		hide();
	}
	
	public void show() {
		Platform.runLater(() -> {
			stageService.getInstance().show();
		});
	}
	
	public void hide() {
		Platform.runLater(() -> {
			stageService.getInstance().hide();
		});
	}
	
}