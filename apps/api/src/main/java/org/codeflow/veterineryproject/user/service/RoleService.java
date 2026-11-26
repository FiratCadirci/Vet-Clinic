package org.codeflow.veterineryproject.user.service;

import org.codeflow.veterineryproject.user.dto.RoleResponse;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private final UserAdminRepository repository;

    public RoleService(UserAdminRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return repository.findRoles();
    }
}
