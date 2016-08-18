package com.github.axiopisty.osgi.javafx.launcher.provider;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
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
 * 
 * SEE: {@link http://stackoverflow.com/a/39011490/328275} regarding 
 *      components that need to be manually registered with the
 *      service registry. 
 */
@Component(
	name = "com.github.axiopisty.osgi.javafx.launcher", 
	scope = ServiceScope.SINGLETON,
	immediate = true
)
public class Main extends Application {
	
	@Reference
	LogService log;
	
	private final AtomicReference<ServiceRegistration<StageService>> serviceRef = new AtomicReference<>();

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
	void activate() {
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
	void deactivate() {
		log.log(LogService.LOG_INFO, "Stopping the JavaFX application and the OSGi container.");
		unregisterStageService();
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

		// Note we register the service from the JavaFX Application 
		// Thread, not the thread the SCR used to activate this 
		// component.
		registerStageService(primaryStage);
	}
	
	/**
	 * This method manually registers the StageService in the service registry.
	 * Because of this, the "Provides-Capability" OSGi header must also be
	 * set manually. See how that is done in the bnd.bnd file.
	 * 
	 * SEE: {@link SEE: http://stackoverflow.com/questions/39004498/how-to-programmatically-register-a-service-in-the-scr-using-osgi-standard-featur}
	 * 
	 * Manually registering services in the service registry should be avoided.
	 * There are rare circumstances however when it is necessary to manually
	 * register a service. This is one of those cases. The JavaFX application
	 * needs to run in a background thread. The StageService will be registered
	 * at some point in time after this component is activated from the JavaFX
	 * Application Thread. So that's what this method does.
	 * 
	 * NB: The build tab in the bnd.bnd file editor shows a warning stating that:
	 *     
	 *     "The servicefactory:=true directive is set but no service is provided, ignoring it"
	 *     
	 *     This is occurring because we are manually registering the service
	 *     rather than having it set automatically by the {@code @Component}
	 *     annotation.
	 */
	private void registerStageService(Stage primaryStage) {
		serviceRef.compareAndSet(
				null,
				bundle()
					.getBundleContext()
					.registerService(StageService.class, new StageProvider(primaryStage), null)
			);
	}
	
	/**
	 * Since this component manually registers the StageService in the service
	 * registry, it must also unregister the service if the component is
	 * deactivated. So that's what this method does.
	 */
	private void unregisterStageService() {
		ServiceRegistration<StageService> stageService = serviceRef.get();
		if(stageService != null) {
			try {
				stageService.unregister();
			} catch (IllegalStateException ise) {
				// We just wanted to make sure the service is not registered.
				// It's not, so just move on.
			}
		}
	}
	
	private Bundle bundle() {
		return FrameworkUtil.getBundle(getClass());
	}
	
}
