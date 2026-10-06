package io.github.harshjoeyit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan
public class ExpenseSimplifiedApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseSimplifiedApplication.class, args);
    }
}
