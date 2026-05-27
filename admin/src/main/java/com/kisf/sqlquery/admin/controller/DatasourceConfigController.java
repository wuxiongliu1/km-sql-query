package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/datasource")
public class DatasourceConfigController {

    private final DatasourceConfigService service;

    public DatasourceConfigController(DatasourceConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DatasourceConfig> create(@RequestBody DatasourceConfig config) {
        return ResponseEntity.ok(service.save(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DatasourceConfig> update(@PathVariable String id, @RequestBody DatasourceConfig config) {
        return ResponseEntity.ok(service.update(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasourceConfig> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<List<DatasourceConfig>> list() {
        return ResponseEntity.ok(service.list());
    }
}
