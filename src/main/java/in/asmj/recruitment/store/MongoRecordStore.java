package in.asmj.recruitment.store;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
@Profile("test | prod")
public class MongoRecordStore implements RecordStore {
  private final MongoTemplate mongo;
  public MongoRecordStore(MongoTemplate mongo){this.mongo=mongo;}
  public Map<String,Object> save(String type,String id,Map<String,Object> payload){
    Map<String,Object> doc=new LinkedHashMap<>(payload);doc.put("_id",type+":"+id);doc.put("recordType",type);doc.put("recordId",id);
    mongo.save(doc,"app_records");doc.remove("_id");doc.remove("recordType");doc.remove("recordId");return doc;
  }
  public Optional<Map<String,Object>> find(String type,String id){
    Map doc=mongo.findById(type+":"+id,Map.class,"app_records");if(doc==null)return Optional.empty();return Optional.of(clean(doc));
  }
  public List<Map<String,Object>> findAll(String type){
    return mongo.find(Query.query(Criteria.where("recordType").is(type)),Map.class,"app_records").stream().map(this::clean).toList();
  }
  public void delete(String type,String id){mongo.remove(Query.query(Criteria.where("_id").is(type+":"+id)),"app_records");}
  public long count(String type){return mongo.count(Query.query(Criteria.where("recordType").is(type)),"app_records");}
  private Map<String,Object> clean(Map doc){Map<String,Object> c=new LinkedHashMap<>(doc);c.remove("_id");c.remove("recordType");c.remove("recordId");return c;}
}
