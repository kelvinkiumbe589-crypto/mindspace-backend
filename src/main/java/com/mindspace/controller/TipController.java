package com.mindspace.controller;

import com.mindspace.dto.TipDto;
import com.mindspace.service.TipService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** "Buy me a coffee" contributions. Public — anyone can support. */
@RestController
@RequestMapping("/api/tips")
public class TipController {

    private final TipService tipService;

    public TipController(TipService tipService) {
        this.tipService = tipService;
    }

    @PostMapping
    public TipDto.Response create(@RequestBody TipDto.CreateRequest req) {
        return tipService.create(req);
    }

    @PostMapping("/{id}/paid")
    public TipDto.Response paid(@PathVariable UUID id, @RequestBody(required = false) TipDto.PaidRequest req) {
        return tipService.markPaid(id, req == null ? null : req.getOrderTrackingId());
    }
}
