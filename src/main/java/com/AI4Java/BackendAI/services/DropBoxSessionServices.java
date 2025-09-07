package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.repository.DropboxSessionRepo;
import com.AI4Java.BackendAI.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;

public class DropBoxSessionServices {

    @Autowired
    private DropboxSessionRepo dropboxSessionRepo;

    @Autowired
    private UserRepo userRepo;

}
