package com.AI4Java.BackendAI.repository;

import com.AI4Java.BackendAI.entries.SessionEntries;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DropboxSessionRepo extends MongoRepository<SessionEntries, ObjectId> {
}
