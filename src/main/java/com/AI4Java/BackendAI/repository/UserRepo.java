package com.AI4Java.BackendAI.repository;

import com.AI4Java.BackendAI.entries.UserEntries;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepo extends MongoRepository<UserEntries, ObjectId> {

    UserEntries findByUserName(String username);

    UserEntries findByGmail(String username);

    void deleteByUserName(String username);
}
