package com.mikey.reservation.audit;

import com.mikey.reservation.identity.CurrentUser;
import com.mikey.reservation.shared.api.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditController {
    public record Entry(UUID id, UUID actorId, String action, String entityType, UUID entityId, JsonNode details, Instant createdAt) {}
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public AuditController(CurrentUser current, JdbcTemplate jdbc, JsonMapper json) {this.current=current;this.jdbc=jdbc;this.json=json;}
    @GetMapping @Transactional(readOnly=true)
    public PageResponse<Entry> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) UUID entityId) {
        current.requireAdmin(); PageResponse.validate(page,size);
        String filter=entityId==null?"":" where entity_id=?";
        var count=entityId==null?jdbc.queryForObject("select count(*) from audit_events",Long.class):jdbc.queryForObject("select count(*) from audit_events"+filter,Long.class,entityId);
        Object[] args=entityId==null?new Object[]{size,(long)page*size}:new Object[]{entityId,size,(long)page*size};
        var rows=jdbc.query("select * from audit_events"+filter+" order by created_at desc,id desc limit ? offset ?",(rs,n)->new Entry(rs.getObject("id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getString("action"),rs.getString("entity_type"),rs.getObject("entity_id",UUID.class),json.readTree(rs.getString("details")),rs.getTimestamp("created_at").toInstant()),args);
        return new PageResponse<>(rows,page,size,count);
    }
}
