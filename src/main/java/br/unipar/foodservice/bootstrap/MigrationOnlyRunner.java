package br.unipar.foodservice.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Permite que o pipeline execute o mesmo artefato da aplicação somente para
 * validar/aplicar as migrations e termine com código de sucesso.
 */
@Component
@Order
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.migrations-only", havingValue = "true")
public class MigrationOnlyRunner implements CommandLineRunner {

    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
