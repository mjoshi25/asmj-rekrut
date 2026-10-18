package in.asmj.recruitment.store;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
@Profile("dev")
public class H2RecordStore implements RecordStore {
  private final JdbcTemplate jdbc; private final ObjectMapper mapper;
  public H2RecordStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
  @jakarta.annotation.PostConstruct public void init() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS app_records (record_type VARCHAR(60) NOT NULL, record_id VARCHAR(80) NOT NULL, payload CLOB NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(record_type, record_id))");
  }
  public Map<String,Object> save(String type,String id,Map<String,Object> payload) {
    try { Map<String,Object> record=new LinkedHashMap<>(payload); record.putIfAbsent("id",id); String json=mapper.writeValueAsString(record);
      jdbc.update("MERGE INTO app_records(record_type,record_id,payload,updated_at) KEY(record_type,record_id) VALUES(?,?,?,CURRENT_TIMESTAMP)",type,id,json);
      return record;
    } catch(Exception e){throw new IllegalStateException("Could not save record",e);}
  }
  public Optional<Map<String,Object>> find(String type,String id) {
    return jdbc.query("SELECT payload FROM app_records WHERE record_type=? AND record_id=?",rs->{if(!rs.next())return Optional.empty();Map<String,Object> record=read(rs.getString(1));record.putIfAbsent("id",id);return Optional.of(record);},type,id);
  }
  public List<Map<String,Object>> findAll(String type) {
    return jdbc.query("SELECT record_id,payload FROM app_records WHERE record_type=? ORDER BY updated_at DESC",(rs,n)->{Map<String,Object> record=read(rs.getString("payload"));record.putIfAbsent("id",rs.getString("record_id"));return record;},type);
  }
  public void delete(String type,String id){jdbc.update("DELETE FROM app_records WHERE record_type=? AND record_id=?",type,id);}
  public long count(String type){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM app_records WHERE record_type=?",Long.class,type);return n==null?0:n;}
  private Map<String,Object> read(String json){try{return mapper.readValue(json,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
}
