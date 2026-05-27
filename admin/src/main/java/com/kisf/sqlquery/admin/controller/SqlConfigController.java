package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.model.PagedResult;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sqlConfig")
public class SqlConfigController {

    private final SqlConfigService service;

    public SqlConfigController(SqlConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SqlConfig> create(@RequestBody SqlConfig config) {
        return ResponseEntity.ok(service.save(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SqlConfig> update(@PathVariable Long id, @RequestBody SqlConfig config) {
        return ResponseEntity.ok(service.update(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SqlConfig> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/folders")
    public ResponseEntity<List<String>> folders() {
        return ResponseEntity.ok(service.getFolders());
    }

    @GetMapping("/list")
    public ResponseEntity<PagedResult<SqlConfig>> list(
            @RequestParam(required = false) String sqlPath,
            @RequestParam(required = false) String datasourceId,
            @RequestParam(required = false) String folder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResult.of(service.list(sqlPath, datasourceId, folder, PageRequest.of(page, size))));
    }
}
