package com.code.agent.config.converter;

import com.code.agent.domain.model.Repository;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationPropertiesBinding
public class StringToRepositoryConverter implements Converter<String, Repository> {

    @Override
    public Repository convert(@Nullable String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        String[] parts = source.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository format. Expected 'owner/repo', but got '" + source + "'");
        }

        return new Repository(parts[0], parts[1]);
    }
}
