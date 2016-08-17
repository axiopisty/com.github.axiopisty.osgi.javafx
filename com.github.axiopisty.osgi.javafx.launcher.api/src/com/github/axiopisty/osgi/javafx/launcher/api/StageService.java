package com.github.axiopisty.osgi.javafx.launcher.api;

import org.osgi.annotation.versioning.ProviderType;

import javafx.stage.Stage;

@ProviderType
public interface StageService {
	
	Stage getInstance();
	
}
