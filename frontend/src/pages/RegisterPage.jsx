import React, { useState } from 'react';
import { register } from '../services/authService';

const RegisterPage = () => {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);

    const handleRegister = async (e) => {
        e.preventDefault();
        setLoading(true);
        setMessage('Registering...');
        try {
            await register(username, email, password);
            setMessage('Registration successful! Redirecting to login...');
            setTimeout(() => {
                window.location.href = '/login';
            }, 2000);
        } catch (error) {
            setMessage(error.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="wrapper">
            <main>
                <form className="login_box" onSubmit={handleRegister}>
                    <div className="login-header">
                        <span>Register</span>
                    </div>
                    <div className="input_box">
                        <input type="text" id="username" className="input-field" value={username} onChange={(e) => setUsername(e.target.value)} required />
                        <label htmlFor="username" className="label">Username</label>
                        <i className="bx bx-user icon"></i>
                    </div>
                    <div className="input_box">
                        <input type="email" id="email" className="input-field" value={email} onChange={(e) => setEmail(e.target.value)} required />
                        <label htmlFor="email" className="label">Email</label>
                        <i className="bx bx-envelope icon"></i>
                    </div>
                    <div className="input_box">
                        <input type="password" id="password" className="input-field" value={password} onChange={(e) => setPassword(e.target.value)} required />
                        <label htmlFor="password" className="label">Password</label>
                        <i className="bx bx-lock-alt icon"></i>
                    </div>
                    <div className="input_box">
                        <button type="submit" className="submit" disabled={loading}>{loading ? 'Loading...' : 'Register'}</button>
                    </div>
                    <div style={{ textAlign: 'center', marginTop: '10px' }}>{message}</div>
                    <div className="register">
                        <span>Already registered? <a href="/login">Login</a></span>
                    </div>
                </form>
            </main>
        </div>
    );
};

export default RegisterPage;
