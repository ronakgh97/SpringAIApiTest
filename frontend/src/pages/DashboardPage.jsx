import React, { useState, useEffect } from 'react';
import { validateToken } from '../services/authService';

import FeatureCard from '../components/FeatureCard';

const DashboardPage = () => {
    const [user, setUser] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        const token = localStorage.getItem('authToken');
        const storedUser = JSON.parse(localStorage.getItem('user'));

        if (!token || !storedUser) {
            setError('You need to be logged in to view this page.');
            setTimeout(() => {
                window.location.href = '/login';
            }, 3000);
            return;
        }

        setUser(storedUser);

        const checkToken = async () => {
            const isValid = await validateToken(token);
            if (!isValid) {
                localStorage.removeItem('authToken');
                localStorage.removeItem('user');
                setError('Session expired. Please log in again.');
                setTimeout(() => {
                    window.location.href = '/login';
                }, 3000);
            }
        };

        checkToken();
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('user');
        window.location.href = '/login';
    };

    if (error) {
        return <div style={{ textAlign: 'center', marginTop: '20px', color: '#f44336' }}>{error}</div>;
    }

    if (!user) {
        return <div>Loading...</div>;
    }

    return (
        <div className="wrapper">
            <main>
                <div className="dashboard-header">
                    <div className="user-info">
                        <div className="user-avatar">
                            <i className='bx bx-user'></i>
                        </div>
                        <div>
                            <h3>{user.userName}</h3>
                            <p>{user.gmail}</p>
                        </div>
                    </div>
                    <button onClick={handleLogout} className="logout-btn">
                        <i className='bx bx-log-out'></i> Logout
                    </button>
                </div>

                <div className="welcome-section">
                    <h1>Welcome to Your Dashboard</h1>
                    <p>Manage your account, access features, and track your activity all in one place.</p>
                </div>

                <div className="features-grid">
                    <FeatureCard icon="bx-user-circle" title="Profile Management" description="Update your personal information and manage your account settings." />
                    <FeatureCard icon="bx-cog" title="Settings" description="Customize your experience and configure application preferences." />
                    <FeatureCard icon="bx-history" title="Activity Log" description="View your recent activity and track your usage history." />
                </div>
            </main>
        </div>
    );
};

export default DashboardPage;
