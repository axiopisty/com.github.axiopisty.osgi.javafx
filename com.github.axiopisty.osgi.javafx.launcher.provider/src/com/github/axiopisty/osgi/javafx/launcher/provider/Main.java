package com.github.axiopisty.osgi.javafx.launcher.provider;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.log.LogService;

import com.github.axiopisty.osgi.javafx.launcher.api.StageService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * This purpose of this component is to manage the JavaFX application 
 * life cycle within the context of an OSGi container, specifically 
 * within the context of the Service Component Runtime (SCR) life cycle.
 * 
 * Unfortunately, these two life cycles do not mix well. While the 
 * life cycle for SCR components is designed to allow components 
 * to be restarted, the life cycle of a JavaFX application is 
 * designed to only be run once within the life cycle of the JVM in 
 * which it is running. JavaFX applications are not allowed to
 * restart during the life cycle of the JVM. Instead, the entire
 * JVM must be restarted.
 * 
 * To accommodate this difference this component must be singleton 
 * scoped. It is also configured to be activated immediately. This
 * is to maximize the amount of time provided for the Java FX 
 * application to launch before this component is referenced by 
 * some other component. When this component is activated the 
 * JavaFX application is launched. When this component is deactivated
 * both the JavaFX application and the OSGi container will be
 * shut down so the entire JVM will shut down.
 * 
 * Once this component is activated and the JavaFX application
 * has been launched, this component will publish a {@code StageService} 
 * in the service registry that can be used by other bundles.
 * 
 * This component is motivated by 
 * {@link http://paulonjava.blogspot.com/2014/11/making-javafx-better-with-osgi.html}
 * but it is modified to use the OSGi SCR annotations instead of the
 * Apache Felix DM.
 */
@Component(
	name = "com.github.axiopisty.osgi.javafx.launcher", 
	scope = ServiceScope.SINGLETON,
	immediate = true
)
public class Main extends Application {
	
	@Reference
	LogService log;

	/**
	 * This method is invoked by the SCR when this component is
	 * activated. It is used to launch the JavaFX application.
	 * 
	 * The JavaFX launch method blocks the current thread until 
	 * the JavaFX application is closed. Calling it in this thread
	 * will therefore block whichever thread is activating this
	 * component in the SCR. We don't want to block that thread,
	 * so we launch the JavaFX application in it's own thread.
	 * We set it's daemon status to {@code true} so if the OSGi 
	 * container is shut down by some other means, the thread used
	 * to launch the application will not prevent the JVM from 
	 * shutting down.  
	 */
	@Activate
	void activate(ComponentContext cc, BundleContext bc, Map<String, Object> config) {
		final Thread t = Executors.defaultThreadFactory().newThread(() -> {
			log.log(LogService.LOG_INFO, "Starting the JavaFX application.");
			Platform.setImplicitExit(false);
			launch();
		});
		t.setDaemon(true);
		t.setContextClassLoader(getClass().getClassLoader());
		t.start();
	}
	
	/**
	 * This method is invoked by the SCR when this component is
	 * deactivated. It shuts down both the JavaFX application
	 * and the OSGi container.
	 */
	@Deactivate
	void deactivate(ComponentContext cc, BundleContext bc, Map<String, Object> config) {
		log.log(LogService.LOG_INFO, "Stopping the JavaFX application and the OSGi container.");
		Platform.exit();
		try {
			// Stopping bundle 0 will cause the OSGi container to shutdown
			// which will allow the JVM to terminate gracefully.
			bundle().getBundleContext().getBundle(0).stop();
		} catch (BundleException e) {
			log.log(LogService.LOG_WARNING, e.getMessage(), e);
		}
	}

	/**
	 * The entry point into the JavaFX application.
	 * Invoked on the JavaFX application thread.
	 */
	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setOnCloseRequest(event -> {
			try {
				// A requests to close the primary stage is interpreted 
				// to mean to shut down the application. Stopping this 
				// bundle will cause the SCR to deactivate it which will 
				// cause the entire application to shut down. See the
				// deactivate method.
				bundle().stop();
			} catch (BundleException e) {
				log.log(LogService.LOG_WARNING, e.getMessage(), e);
			}
		});
		
		bundle()
			.getBundleContext()
			.registerService(StageService.class, new StageProvider(primaryStage), null);
	}
	
	private Bundle bundle() {
		return FrameworkUtil.getBundle(getClass());
	}
	
}
