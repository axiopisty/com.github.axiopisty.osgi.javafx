package com.github.axiopisty.osgi.javafx.launcher.application;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import com.github.axiopisty.osgi.javafx.launcher.api.StageService;

import osgi.enroute.debug.api.Debug;

@Component(
	name="com.github.axiopisty.osgi.javafx.launcher",
	property = {
		Debug.COMMAND_SCOPE + "=test",
		Debug.COMMAND_FUNCTION + "=func"
	},
	service=LauncherApplication.class,
	immediate=true
)
public class LauncherApplication {
	
	// https://github.com/paulbakker/javafx-osgi-example/blob/master/example.javafx.launcher/src/javafxtest/App.java
	
	@Reference
	StageService stageService;

	public void func() {
		System.out.printf("stageService: %s\n", stageService);
	}
	
	@Activate
	void activate() {
		System.out.println("application - activate");
	}
	
	@Deactivate
	void deactivate() {
		System.out.println("application - deactivate");
	}
}
