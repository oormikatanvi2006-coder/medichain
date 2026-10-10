package org.springframework.scheduling.annotation; public @interface Scheduled { String fixedDelayString() default ""; String initialDelayString() default ""; }
