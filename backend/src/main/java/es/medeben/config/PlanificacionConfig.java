package es.medeben.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas y trabajo en segundo plano del backend (hoy: la purga
 * diaria de sesiones y el envío async del correo de verificación). Aparte
 * para que quien busque "qué corre solo en este servidor" tenga UN sitio.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class PlanificacionConfig {
}
