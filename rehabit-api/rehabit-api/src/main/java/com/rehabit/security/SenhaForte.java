package com.rehabit.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Senha nova dentro da {@link PoliticaDeSenha}. A mensagem diz qual regra falhou. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PoliticaDeSenha.Validador.class)
public @interface SenhaForte {

    String message() default "Senha fora da política.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
