package com.github.axiopisty.osgi.javafx.launcher.provider;

import com.github.axiopisty.osgi.javafx.launcher.api.StageService;

import javafx.stage.Stage;

public class StageProvider implements StageService {

	private final Stage stage;

	public StageProvider(Stage stage) {
		this.stage = stage;
	}

	@Override
	public Stage getInstance() {
		return stage;
	}
	
}
