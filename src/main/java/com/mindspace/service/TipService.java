package com.mindspace.service;

import com.mindspace.dto.TipDto;
import com.mindspace.model.Tip;
import com.mindspace.repository.TipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TipService {

    private final TipRepository repo;

    public TipService(TipRepository repo) {
        this.repo = repo;
    }

    public TipDto.Response create(TipDto.CreateRequest req) {
        if (req.getAmount() <= 0) throw new IllegalArgumentException("Enter an amount greater than 0");
        Tip t = new Tip();
        t.setAmount(req.getAmount());
        t.setName(req.getName());
        t.setMessage(req.getMessage());
        t.setStatus(Tip.Status.PENDING);
        return toResp(repo.save(t));
    }

    public TipDto.Response markPaid(UUID id, String orderTrackingId) {
        Tip t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Contribution not found"));
        t.setStatus(Tip.Status.PAID);
        t.setOrderTrackingId(orderTrackingId);
        return toResp(repo.save(t));
    }

    public List<TipDto.Response> list() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toResp).toList();
    }

    public int totalPaid() {
        return repo.findAll().stream().filter(t -> t.getStatus() == Tip.Status.PAID).mapToInt(Tip::getAmount).sum();
    }

    public long countPaid() {
        return repo.findAll().stream().filter(t -> t.getStatus() == Tip.Status.PAID).count();
    }

    private TipDto.Response toResp(Tip t) {
        TipDto.Response r = new TipDto.Response();
        r.id = t.getId().toString();
        r.name = t.getName();
        r.message = t.getMessage();
        r.amount = t.getAmount();
        r.status = t.getStatus().name();
        r.createdAt = t.getCreatedAt() == null ? null : t.getCreatedAt().toString();
        return r;
    }
}
