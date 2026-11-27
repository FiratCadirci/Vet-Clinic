package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanOption;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;
import org.codeflow.veterineryproject.subscription.repository.PlanLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FG-K1: aktif şube ve kullanıcı limitleri. Çağıranın transaction'ı içinde çalışır; organizasyon satırı kilitlendiği
 * için sayım ile ekleme arasında başka bir işlem araya giremez.
 */
@Service
public class PlanLimitService {

    private final PlanLimitRepository repository;

    public PlanLimitService(PlanLimitRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanAddUser() {
        repository.lockAndCheck(PlanResource.USERS).orThrow(PlanResource.USERS);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanAddBranch() {
        repository.lockAndCheck(PlanResource.BRANCHES).orThrow(PlanResource.BRANCHES);
    }

    @Transactional(readOnly = true)
    public PlanUsage usage() {
        return repository.currentUsage();
    }

    @Transactional(readOnly = true)
    public List<PlanOption> activePlans() {
        return repository.activePlans();
    }
}
