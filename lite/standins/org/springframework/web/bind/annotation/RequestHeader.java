package org.springframework.web.bind.annotation; public @interface RequestHeader { String name() default ""; boolean required() default true; }
