package org.codeflow.veterineryproject.search.service;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.search.dto.GlobalSearchResponse;
import org.codeflow.veterineryproject.search.repository.GlobalSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class GlobalSearchService {

    static final int MIN_LENGTH = 2;
    static final int LIMIT = 10;
    private static final Pattern PHONE_NOISE = Pattern.compile("[\\s\\-().]");

    private final GlobalSearchRepository repository;

    public GlobalSearchService(GlobalSearchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < MIN_LENGTH) {
            throw new BusinessRuleException("Arama için en az %d karakter girilmelidir.".formatted(MIN_LENGTH));
        }
        String phone = normalizePhone(query);
        return new GlobalSearchResponse(query, repository.clients(query, phone, LIMIT),
                repository.animals(query, phone, LIMIT));
    }

    static String normalizePhone(String query) {
        String digits = PHONE_NOISE.matcher(query).replaceAll("");
        return digits.matches("\\+?[0-9]{3,15}") ? digits : null;
    }
}
