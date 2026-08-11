package ng.cvfacil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita jobs @Scheduled fora do profile local — hoje usado por
 * RootEmailEnforcementService. O profile local tem seu proprio @EnableScheduling em
 * LocalDataPersistenceConfig (backup periodico do Postgres embarcado); mantido separado para nao
 * acoplar os dois usos.
 */
@Configuration
@Profile("!local")
@EnableScheduling
public class SchedulingConfig {}
