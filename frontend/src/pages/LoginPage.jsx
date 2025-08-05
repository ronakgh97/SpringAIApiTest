import React, { useState } from 'react';
import { login, checkSystemStatus } from '../services/authService';

const LoginPage = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);

    const handleLogin = async (e) => {
        e.preventDefault();
        setLoading(true);
        setMessage('Logging in...');
        try {
            const result = await login(username, password);
            localStorage.setItem('authToken', result.data.token);
            localStorage.setItem('user', JSON.stringify(result.data.user));
            setMessage('Login successful! Redirecting...');
            window.location.href = '/dashboard';
        } catch (error) {
            setMessage(error.message);
        } finally {
            setLoading(false);
        }
    };

    const handleCheckStatus = async () => {
        setLoading(true);
        setMessage('Checking system status...');
        try {
            const result = await checkSystemStatus();
            setMessage(`System Status: ${result.status} - ${result.service} v${result.version}`);
        } catch (error) {
            setMessage(error.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="wrapper">
            <main>
                <form className="login_box" onSubmit={handleLogin}>
                    <div className="login-header">
                        <span>Login</span>
                    </div>
                    <div className="input_box">
                        <input type="text" id="username" className="input-field" value={username} onChange={(e) => setUsername(e.target.value)} required />
                        <label htmlFor="username" className="label">Username</label>
                        <i className="bx bx-user icon"></i>
                    </div>
                    <div className="input_box">
                        <input type="password" id="password" className="input-field" value={password} onChange={(e) => setPassword(e.target.value)} required />
                        <label htmlFor="password" className="label">Password</label>
                        <i className="bx bx-lock-alt icon"></i>
                    </div>
                    <div className="input_box">
                        <button type="submit" className="submit" disabled={loading}>{loading ? 'Loading...' : 'Login'}</button>
                    </div>
                    <div style={{ textAlign: 'center', marginTop: '10px' }}>{message}</div>
                    <div className="register">
                        <span>Register here {'->'} <a href="/register">Register</a></span>
                    </div>
                    <div className="input_box">
                        <button type="button" className="status" onClick={handleCheckStatus} disabled={loading}>{loading ? 'Loading...' : 'Check Status'}</button>
                    </div>
                </form>
            </main>
        </div>
    );
};

export default LoginPage;
