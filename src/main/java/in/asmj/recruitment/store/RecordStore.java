package in.asmj.recruitment.store;
import java.util.*;
public interface RecordStore {
  Map<String,Object> save(String type, String id, Map<String,Object> payload);
  Optional<Map<String,Object>> find(String type, String id);
  List<Map<String,Object>> findAll(String type);
  void delete(String type, String id);
  long count(String type);
}
