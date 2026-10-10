package org.springframework.web.bind.annotation; public @interface ExceptionHandler { Class<? extends Throwable>[] value() default {}; }
