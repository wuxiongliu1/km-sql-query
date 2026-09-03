package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import com.kisf.sqlquery.core.cache.DataSourceHealth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/datasource")
public class DatasourceConfigController {

    private final DatasourceConfigService service;
    private final DataSourceCache dataSourceCache;

    public DatasourceConfigController(DatasourceConfigService service, DataSourceCache dataSourceCache) {
        this.service = service;
        this.dataSourceCache = dataSourceCache;
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

    @PostMapping("/test")
    public ResponseEntity<DataSourceHealth> test(@RequestBody DatasourceConfig config) {
        return ResponseEntity.ok(dataSourceCache.testConnection(config));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<DataSourceHealth> health(@PathVariable String id) {
        return ResponseEntity.ok(dataSourceCache.checkHealth(id));
    }
}
