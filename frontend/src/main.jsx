import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './global.css';
import './login_register.css';
import './dashboard.css';

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>
);